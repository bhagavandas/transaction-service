package com.example.transactions;

import com.example.transactions.dto.TransactionRequest;
import com.example.transactions.model.*;
import com.example.transactions.repository.*;
import com.example.transactions.service.TransactionProcessingService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TransactionProcessingServiceTest {
    @Autowired TransactionProcessingService service;
    @Autowired TransactionRepository txRepo;
    @Autowired AccountRepository accountRepo;

    private TransactionRequest req(String tx, String request, String account, TransactionType type,
                                    String amount, long seq, boolean fail) {
        return new TransactionRequest(tx, request, account, type, new BigDecimal(amount), "INR", seq, fail);
    }

    @Test void duplicateTransactionIdIsNotInsertedTwiceAndReturnsDuplicateStatus() {
        service.submit(req("DUP-T1", "DUP-R1", "DUP-ACC", TransactionType.CREDIT, "100", 1, false));
        var duplicate = service.submit(req("DUP-T1", "DUP-R1-RETRY", "DUP-ACC", TransactionType.CREDIT, "100", 1, false));
        assertEquals(TransactionStatus.DUPLICATE, duplicate.status());
        assertEquals(1, txRepo.findAll().stream().filter(x -> x.getTransactionId().equals("DUP-T1")).count());
    }

    @Test void duplicateRequestIdIsAlsoDetected() {
        service.submit(req("DUP-TX-A", "SAME-REQUEST", "DUP-REQ-ACC", TransactionType.CREDIT, "10", 1, false));
        var duplicate = service.submit(req("DUP-TX-B", "SAME-REQUEST", "DUP-REQ-ACC", TransactionType.CREDIT, "10", 1, false));
        assertEquals(TransactionStatus.DUPLICATE, duplicate.status());
        assertFalse(txRepo.findByTransactionId("DUP-TX-B").isPresent());
    }

    @Test void outOfOrderRequestWaitsThenProcessesInSequence() {
        service.submit(req("ORDER-T2", "ORDER-R2", "ORDER-ACC", TransactionType.CREDIT, "40", 2, false));
        service.processBatch();
        assertEquals(TransactionStatus.PENDING, txRepo.findByTransactionId("ORDER-T2").orElseThrow().getStatus());
        service.submit(req("ORDER-T1", "ORDER-R1", "ORDER-ACC", TransactionType.CREDIT, "100", 1, false));
        service.processBatch();
        assertEquals(TransactionStatus.PROCESSED, txRepo.findByTransactionId("ORDER-T1").orElseThrow().getStatus());
        assertEquals(TransactionStatus.PROCESSED, txRepo.findByTransactionId("ORDER-T2").orElseThrow().getStatus());
        assertEquals(new BigDecimal("140.00"), accountRepo.findById("ORDER-ACC").orElseThrow().getBalance());
    }

    @Test void transientFailureIsRetriedOnNextBatchWithoutDuplicateBalanceEffect() {
        service.submit(req("RETRY-T1", "RETRY-R1", "RETRY-ACC", TransactionType.CREDIT, "50", 1, true));
        service.processBatch();
        assertEquals(TransactionStatus.RETRY_PENDING, txRepo.findByTransactionId("RETRY-T1").orElseThrow().getStatus());
        service.processBatch();
        assertEquals(TransactionStatus.PROCESSED, txRepo.findByTransactionId("RETRY-T1").orElseThrow().getStatus());
        assertEquals(2, txRepo.findByTransactionId("RETRY-T1").orElseThrow().getAttempts());
        assertEquals(new BigDecimal("50.00"), accountRepo.findById("RETRY-ACC").orElseThrow().getBalance());
    }


    @Test void retryExhaustionMarksFailedAfterExactlyThreeAttemptsAndNeverChangesBalance() {
        TransactionRequest request = new TransactionRequest("RETRY-EXHAUST-T1", "RETRY-EXHAUST-R1",
                "RETRY-EXHAUST-ACC", TransactionType.CREDIT, new BigDecimal("75"), "INR", 1,
                true, 3);
        service.submit(request);

        service.processBatch();
        assertEquals(TransactionStatus.RETRY_PENDING,
                txRepo.findByTransactionId("RETRY-EXHAUST-T1").orElseThrow().getStatus());
        service.processBatch();
        assertEquals(TransactionStatus.RETRY_PENDING,
                txRepo.findByTransactionId("RETRY-EXHAUST-T1").orElseThrow().getStatus());
        var thirdBatch = service.processBatch();
        var record = txRepo.findByTransactionId("RETRY-EXHAUST-T1").orElseThrow();
        assertEquals(TransactionStatus.FAILED, record.getStatus());
        assertEquals(3, record.getAttempts());
        assertTrue(((Number) thirdBatch.get("failed")).intValue() >= 1);
        assertEquals(new BigDecimal("0.00"), accountRepo.findById("RETRY-EXHAUST-ACC").orElseThrow().getBalance());

        service.processBatch();
        assertEquals(new BigDecimal("0.00"), accountRepo.findById("RETRY-EXHAUST-ACC").orElseThrow().getBalance());
        assertEquals(3, txRepo.findByTransactionId("RETRY-EXHAUST-T1").orElseThrow().getAttempts());
    }

    @Test void missingSequenceRemainsPendingUntilGapIsFilled() {
        service.submit(req("GAP-T3", "GAP-R3", "GAP-ACC", TransactionType.CREDIT, "30", 3, false));
        service.processBatch();
        assertEquals(TransactionStatus.PENDING, txRepo.findByTransactionId("GAP-T3").orElseThrow().getStatus());
        service.submit(req("GAP-T1", "GAP-R1", "GAP-ACC", TransactionType.CREDIT, "10", 1, false));
        service.processBatch();
        assertEquals(TransactionStatus.PROCESSED, txRepo.findByTransactionId("GAP-T1").orElseThrow().getStatus());
        assertEquals(TransactionStatus.PENDING, txRepo.findByTransactionId("GAP-T3").orElseThrow().getStatus());
        service.submit(req("GAP-T2", "GAP-R2", "GAP-ACC", TransactionType.CREDIT, "20", 2, false));
        service.processBatch();
        assertEquals(TransactionStatus.PROCESSED, txRepo.findByTransactionId("GAP-T2").orElseThrow().getStatus());
        assertEquals(TransactionStatus.PROCESSED, txRepo.findByTransactionId("GAP-T3").orElseThrow().getStatus());
        assertEquals(new BigDecimal("60.00"), accountRepo.findById("GAP-ACC").orElseThrow().getBalance());
    }

    @Test void invalidNegativeAmountFailsBeanValidation() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        TransactionRequest invalid = req("BAD-T1", "BAD-R1", "BAD-ACC", TransactionType.CREDIT, "-1", 1, false);
        assertFalse(validator.validate(invalid).isEmpty());
    }

    @Test void insufficientBalanceMovesDebitToFailedAndDoesNotChangeBalance() {
        service.submit(req("DEBIT-T1", "DEBIT-R1", "DEBIT-ACC", TransactionType.DEBIT, "20", 1, false));
        service.processBatch();
        assertEquals(TransactionStatus.FAILED, txRepo.findByTransactionId("DEBIT-T1").orElseThrow().getStatus());
        assertEquals(BigDecimal.ZERO.setScale(2), accountRepo.findById("DEBIT-ACC").orElseThrow().getBalance());
    }
}
