package com.example.transactions.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "accounts")
public class Account {
	@Id
	private String accountId;
	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal balance = BigDecimal.ZERO;
	@Column(nullable = false)
	private long lastSequence = 0;

	protected Account() {
	}

	public Account(String id) {
		this.accountId = id;
	}

	public String getAccountId() {
		return accountId;
	}

	public BigDecimal getBalance() {
		return balance;
	}

	public long getLastSequence() {
		return lastSequence;
	}

	public void apply(BigDecimal amount, boolean credit) {
		balance = credit ? balance.add(amount) : balance.subtract(amount);
	}

	public void setLastSequence(long value) {
		lastSequence = value;
	}
}
