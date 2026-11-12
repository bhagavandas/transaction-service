package com.example.transactions.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transaction_records", uniqueConstraints = {
		@UniqueConstraint(name = "uk_txn_id", columnNames = "transaction_id"),
		@UniqueConstraint(name = "uk_request_id", columnNames = "request_id"),
		@UniqueConstraint(name = "uk_account_sequence", columnNames = { "account_id", "sequence_number" }) })
public class TransactionRecord {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(name = "transaction_id", nullable = false)
	private String transactionId;
	@Column(name = "request_id", nullable = false)
	private String requestId;
	@Column(name = "account_id", nullable = false)
	private String accountId;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TransactionType type;
	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal amount;
	@Column(nullable = false, length = 3)
	private String currency;
	@Column(name = "sequence_number", nullable = false)
	private long sequenceNumber;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TransactionStatus status;
	@Column(nullable = false)
	private int attempts = 0;
	@Column(nullable = false)
	private int failureAttemptsRequired;
	private String failureReason;
	private Instant createdAt = Instant.now();

	protected TransactionRecord() {
	}

	public TransactionRecord(String tx, String req, String acc, TransactionType type, BigDecimal amount,
			String currency, long seq, int failureAttemptsRequired) {
		this.transactionId = tx;
		this.requestId = req;
		this.accountId = acc;
		this.type = type;
		this.amount = amount;
		this.currency = currency;
		this.sequenceNumber = seq;
		this.status = TransactionStatus.RECEIVED;
		this.failureAttemptsRequired = failureAttemptsRequired;
	}

	public Long getId() {
		return id;
	}

	public String getTransactionId() {
		return transactionId;
	}

	public String getRequestId() {
		return requestId;
	}

	public String getAccountId() {
		return accountId;
	}

	public TransactionType getType() {
		return type;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public String getCurrency() {
		return currency;
	}

	public long getSequenceNumber() {
		return sequenceNumber;
	}

	public TransactionStatus getStatus() {
		return status;
	}

	public int getAttempts() {
		return attempts;
	}

	public String getFailureReason() {
		return failureReason;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public boolean shouldFailAttempt() {
		return attempts <= failureAttemptsRequired;
	}

	public void setStatus(TransactionStatus s) {
		status = s;
	}

	public void incrementAttempts() {
		attempts++;
	}

	public void setFailureReason(String s) {
		failureReason = s;
	}
}
