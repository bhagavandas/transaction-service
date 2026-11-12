package com.example.transactions.repository;

import com.example.transactions.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface TransactionRepository extends JpaRepository<TransactionRecord, Long> {
	Optional<TransactionRecord> findByTransactionId(String id);

	Optional<TransactionRecord> findByRequestId(String id);

	List<TransactionRecord> findByAccountIdAndStatusInOrderBySequenceNumberAsc(String accountId,
			Collection<TransactionStatus> statuses);

	List<TransactionRecord> findAllByOrderByCreatedAtAsc();
}
