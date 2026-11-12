# Idempotent Background Transaction Processing Service

A Java 17 / Spring Boot 3 REST service for the FDE L2 assessment. It demonstrates request deduplication, per-account sequence ordering, retry-safe processing, validation, H2 persistence, status reporting and tests.

## Requirements
- JDK 17+
- Maven 3.8+

## Run
```bash
mvn clean test
mvn spring-boot:run
```
The app starts at `http://localhost:8080`. H2 console: `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:transactions`, user `sa`, blank password).

## API
- `POST /api/transactions` — accept a transaction (HTTP 202)
- `POST /api/transactions/process` — run one processing batch
- `GET /api/transactions` — list all transactions
- `GET /api/transactions/{transactionId}` — get one transaction
- `GET /api/summary` — counts by status
- `GET /api/accounts/{accountId}` — balance and last processed sequence

Example request:
```json
{
  "transactionId": "TXN-1001",
  "requestId": "REQ-1001",
  "accountId": "ACC-1",
  "transactionType": "CREDIT",
  "amount": 100.00,
  "currency": "INR",
  "sequenceNumber": 1,
  "simulateTransientFailure": false
}
```

## Quick demo (out-of-order + duplicate)
Submit sequence 2 first, then sequence 1. Run processing. Sequence 2 remains waiting until sequence 1 is available, then both are processed in order. Submit the same `transactionId` or `requestId` again: the existing record is returned and no second ledger effect is created.

```bash
curl -X POST http://localhost:8080/api/transactions -H "Content-Type: application/json" -d '{"transactionId":"TX-2","requestId":"REQ-2","accountId":"ACC-1","transactionType":"CREDIT","amount":40,"currency":"INR","sequenceNumber":2}'
curl -X POST http://localhost:8080/api/transactions -H "Content-Type: application/json" -d '{"transactionId":"TX-1","requestId":"REQ-1","accountId":"ACC-1","transactionType":"CREDIT","amount":100,"currency":"INR","sequenceNumber":1}'
curl -X POST http://localhost:8080/api/transactions/process
curl http://localhost:8080/api/summary
curl http://localhost:8080/api/accounts/ACC-1
```

## Retry simulation and maximum attempts
- `simulateTransientFailure: true` simulates one transient failure. The first batch moves the transaction to `RETRY_PENDING`; the second batch succeeds.
- Optional `simulatedFailureCount` controls how many attempts should fail. Set it to `3` to test retry exhaustion. The worker makes at most **3 total attempts**; after the third failure the transaction becomes terminal `FAILED`.
- Failed attempts never change the account balance. The balance and `lastSequence` advance only after a successful processing attempt, so retries cannot apply the same credit/debit twice.
- Retries occur on a later `/api/transactions/process` call, not in a tight loop.

Example retry-exhaustion request:
```json
{
  "transactionId": "TX-RETRY-FAIL",
  "requestId": "REQ-RETRY-FAIL",
  "accountId": "ACC-RETRY-FAIL",
  "transactionType": "CREDIT",
  "amount": 75.00,
  "currency": "INR",
  "sequenceNumber": 1,
  "simulateTransientFailure": true,
  "simulatedFailureCount": 3
}
```
Run `POST /api/transactions/process` three times. The status transitions `RETRY_PENDING`, `RETRY_PENDING`, `FAILED`; `attempts` ends at 3 and the account balance remains `0.00`.

## Postman collection
Import `postman/Idempotent-Transaction-Service.postman_collection.json` into Postman. Set `baseUrl` to `http://localhost:8080`, then run the collection in order. It covers a sequence gap, out-of-order recovery, one-time retry, and retry exhaustion. The collection creates a unique run ID for each run.

## Design decisions and trade-offs
- **Idempotency:** transaction ID and request ID are checked before insert; database unique constraints provide a second line of defense. A repeated key returns the existing record.
- **Ordering:** sequence numbers are scoped to each account. Only `lastSequence + 1` is eligible. Gaps are explicitly marked `PENDING`; a failed transaction blocks later sequence numbers to avoid silently applying events out of order.
- **Consistency:** balance and transaction status are updated in one database transaction. H2 is used for a self-contained demo.
- **Resilience:** retry failures are tracked in persisted attempt state; the worker enforces three total attempts and moves the transaction to terminal `FAILED` after the third failed attempt.
- **Observability:** application logs plus `/api/summary` and transaction status endpoints.
- **Validation:** Bean Validation checks required identifiers, transaction type, positive amount, currency format and positive sequence.
- **Security:** no credentials or customer secrets are logged. H2 console is for local development only.

## Important production-hardening notes
1. Use PostgreSQL (or another durable database), migrations, and database-level atomic idempotency claims.
2. Add optimistic/pessimistic locking or account-level partitioning to handle concurrent workers safely.
3. Add a durable queue/outbox, dead-letter queue, exponential backoff, retry timestamps and maximum-attempt enforcement.
4. Add a proper ledger table with immutable entries; never rely only on a mutable balance.
5. Define reversal/compensation semantics, currency/account validation and business rules.
6. Add structured logs, correlation IDs, metrics, tracing, authentication/authorization, rate limits and secret management.
7. Define sequence-gap timeout/alerting and an operator recovery process.

## Assessment explanation
This is a focused assessment implementation, not a complete banking ledger. The process endpoint is intentionally explicit so the reviewer can reproduce processing. AI assistance may be disclosed honestly: used to scaffold boilerplate and documentation; reviewed and tested the business rules manually before submission.

## Test coverage
The test suite covers duplicate transaction IDs, duplicate request IDs, out-of-order arrival and missing sequence gaps, retry on a later batch, retry exhaustion after three attempts, negative amount validation, and insufficient-balance failure. Run `mvn clean test` to execute them.
