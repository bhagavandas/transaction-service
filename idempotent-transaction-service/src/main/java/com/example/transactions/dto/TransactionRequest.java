package com.example.transactions.dto;

import com.example.transactions.model.TransactionType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record TransactionRequest(@NotBlank String transactionId, @NotBlank String requestId, @NotBlank String accountId,
		@NotNull TransactionType transactionType, @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
		@NotBlank @Pattern(regexp = "[A-Z]{3}") String currency, @Positive long sequenceNumber,
		boolean simulateTransientFailure, @PositiveOrZero Integer simulatedFailureCount) {


	public TransactionRequest(String transactionId, String requestId, String accountId, TransactionType transactionType,
			BigDecimal amount, String currency, long sequenceNumber, boolean simulateTransientFailure) {
		this(transactionId, requestId, accountId, transactionType, amount, currency, sequenceNumber,
				simulateTransientFailure, null);
	}

	public int effectiveFailureCount() {
		if (simulatedFailureCount != null)
			return simulatedFailureCount;
		return simulateTransientFailure ? 1 : 0;
	}
}
