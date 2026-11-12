package com.example.transactions.service;

import com.example.transactions.dto.TransactionRequest;
import com.example.transactions.dto.TransactionResponse;
import com.example.transactions.model.*;
import com.example.transactions.repository.AccountRepository;
import com.example.transactions.repository.TransactionRepository;
import jakarta.transaction.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.logging.Logger;

@Service
public class TransactionProcessingService {
	private static final Logger log = Logger.getLogger(TransactionProcessingService.class.getName());
	private static final int MAX_ATTEMPTS = 3;

	private final TransactionRepository transactions;
	private final AccountRepository accounts;

	public TransactionProcessingService(TransactionRepository transactions, AccountRepository accounts) {
		this.transactions = transactions;
		this.accounts = accounts;
	}

	@Transactional
	public TransactionResponse submit(TransactionRequest req) {
		Optional<TransactionRecord> existing = transactions.findByTransactionId(req.transactionId());
		if (existing.isEmpty()) {
			existing = transactions.findByRequestId(req.requestId());
		}
		if (existing.isPresent()) {
			TransactionRecord record = existing.get();
			log.warning("Duplicate request ignored; transactionId=" + record.getTransactionId());
			return new TransactionResponse(record.getTransactionId(), req.requestId(), record.getAccountId(),
					TransactionStatus.DUPLICATE, record.getSequenceNumber(), record.getAmount(), record.getCurrency(),
					record.getAttempts(), "Duplicate request ignored", record.getCreatedAt());
		}

		TransactionRecord record = new TransactionRecord(req.transactionId(), req.requestId(), req.accountId(),
				req.transactionType(), req.amount(), req.currency(), req.sequenceNumber(), req.effectiveFailureCount());
		try {
			transactions.saveAndFlush(record);
		} catch (DataIntegrityViolationException ex) {
			// Database unique constraints are the final protection against concurrent
			// duplicate inserts.
			log.warning("Concurrent duplicate submission rejected; transactionId=" + req.transactionId());
			throw ex;
		}
		log.info("Transaction received; transactionId=" + record.getTransactionId());
		return TransactionResponse.from(record);
	}

	@Transactional
	public Map<String, Object> processBatch() {
		List<TransactionRecord> all = transactions.findAllByOrderByCreatedAtAsc();
		Set<String> accountIds = new LinkedHashSet<>();
		all.forEach(record -> accountIds.add(record.getAccountId()));

		int processed = 0, pending = 0, failed = 0, retryScheduled = 0;
		for (String accountId : accountIds) {
			Account account = accounts.findById(accountId).orElseGet(() -> accounts.save(new Account(accountId)));
			boolean madeProgress = true;
			while (madeProgress) {
				madeProgress = false;
				List<TransactionRecord> candidates = transactions.findByAccountIdAndStatusInOrderBySequenceNumberAsc(
						accountId, List.of(TransactionStatus.RECEIVED, TransactionStatus.RETRY_PENDING,
								TransactionStatus.PENDING));
				Optional<TransactionRecord> next = candidates.stream()
						.filter(r -> r.getSequenceNumber() == account.getLastSequence() + 1).findFirst();

				if (next.isEmpty()) {
					// Mark records beyond the next expected sequence as explicitly pending.
					candidates.stream().filter(r -> r.getSequenceNumber() > account.getLastSequence() + 1)
							.forEach(r -> r.setStatus(TransactionStatus.PENDING));
					break;
				}

				TransactionRecord record = next.get();
				record.setStatus(TransactionStatus.PROCESSING);
				record.incrementAttempts();

				if (record.shouldFailAttempt()) {
					record.setFailureReason("Simulated transient failure on attempt " + record.getAttempts());
					if (record.getAttempts() >= MAX_ATTEMPTS) {
						record.setStatus(TransactionStatus.FAILED);
						log.severe("Retry limit exhausted; transactionId=" + record.getTransactionId() + ", attempts="
								+ record.getAttempts());
					} else {
						record.setStatus(TransactionStatus.RETRY_PENDING);
						retryScheduled++;
						log.warning("Transient failure; transactionId=" + record.getTransactionId() + ", attempt="
								+ record.getAttempts() + ", retry scheduled");
					}
					// Retry on a later batch; never apply the balance before a successful attempt.
					break;
				}

				if (record.getType() == TransactionType.DEBIT
						&& account.getBalance().compareTo(record.getAmount()) < 0) {
					record.setStatus(TransactionStatus.FAILED);
					record.setFailureReason("Insufficient balance");
					log.severe("Transaction failed; transactionId=" + record.getTransactionId()
							+ ", reason=Insufficient balance");
					// Preserve sequence safety: later events wait for operator resolution.
					break;
				}

				account.apply(record.getAmount(), record.getType() == TransactionType.CREDIT);
				account.setLastSequence(record.getSequenceNumber());
				record.setStatus(TransactionStatus.PROCESSED);
				record.setFailureReason(null);
				processed++;
				madeProgress = true;
				log.info("Transaction processed; transactionId=" + record.getTransactionId() + ", accountId="
						+ accountId + ", attempt=" + record.getAttempts());
			}
		}

		for (TransactionRecord record : transactions.findAll()) {
			if (record.getStatus() == TransactionStatus.RECEIVED
					|| record.getStatus() == TransactionStatus.RETRY_PENDING
					|| record.getStatus() == TransactionStatus.PENDING)
				pending++;
			if (record.getStatus() == TransactionStatus.FAILED)
				failed++;
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("processed", processed);
		result.put("pending", pending);
		result.put("failed", failed);
		result.put("retryScheduled", retryScheduled);
		result.put("total", transactions.count());
		log.info("Processing batch completed; processed=" + processed + ", pending=" + pending + ", failed=" + failed
				+ ", retryScheduled=" + retryScheduled);
		return result;
	}

	@Transactional
	public Map<String, Object> summary() {
		Map<String, Object> counts = new LinkedHashMap<>();
		List<TransactionRecord> records = transactions.findAll();
		for (TransactionStatus status : TransactionStatus.values()) {
			counts.put(status.name(), records.stream().filter(r -> r.getStatus() == status).count());
		}
		counts.put("total", records.size());
		return counts;
	}

	public List<TransactionResponse> list() {
		return transactions.findAllByOrderByCreatedAtAsc().stream().map(TransactionResponse::from).toList();
	}

	public Optional<TransactionResponse> get(String id) {
		return transactions.findByTransactionId(id).map(TransactionResponse::from);
	}

	public Map<String, Object> account(String id) {
		Account account = accounts.findById(id).orElseGet(() -> new Account(id));
		return Map.of("accountId", id, "balance", account.getBalance(), "lastSequence", account.getLastSequence());
	}
}
