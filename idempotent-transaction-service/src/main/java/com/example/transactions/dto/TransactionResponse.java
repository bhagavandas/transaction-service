package com.example.transactions.dto;

import com.example.transactions.model.*;
import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(String transactionId, String requestId, String accountId, TransactionStatus status,
		long sequenceNumber, BigDecimal amount, String currency, int attempts, String failureReason,
		Instant createdAt) {
	public static TransactionResponse from(TransactionRecord r) {
		return new TransactionResponse(r.getTransactionId(), r.getRequestId(), r.getAccountId(), r.getStatus(),
				r.getSequenceNumber(), r.getAmount(), r.getCurrency(), r.getAttempts(), r.getFailureReason(),
				r.getCreatedAt());
	}
}
