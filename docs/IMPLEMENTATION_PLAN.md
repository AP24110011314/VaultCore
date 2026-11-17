# Implementation Plan

The project is built in eight phases. Each phase compiles on its own and ends in a clear definition of done.
The phases map to the commit sequence in [COMMIT_PLAN.md](COMMIT_PLAN.md).

Base path used below: `src/main/java/com/vaultcore/wallet` (abbreviated `J/`) and `src/test/java/com/vaultcore/wallet` (`T/`).

## Phase 1: Foundation

Gradle build (Java 21, Spring Boot 4.0.2, springdoc, PostgreSQL driver, H2 for tests), Gradle wrapper, ignore files,
`application.yml`, `VaultCoreApplication`.

Done when: `./gradlew build -x test` succeeds.

## Phase 2: Documentation

README plus the `docs/` set. Written up front so the design (ledger, locking, idempotency) is fixed before code.

Done when: each document agrees with the others on endpoints, schema and flows.

## Phase 3: Domain

1. `J/enums`: `TransactionType`, `LedgerEntryType`, `AccountType`, `Status`.
2. `J/exception`: `ResourceNotFoundException`, `InsufficientFundsException`.
3. `J/model`: `Asset`, `User`, `Account` (credit/debit rules), `Transaction`, `LedgerEntry` (with `balanceAfter`).
4. `J/repository`: five Spring Data repositories, including
   `findByIdForUpdate` (`PESSIMISTIC_WRITE`), id-only account lookups, `findByUserEmail`, `sumAmountsByAccountId`.

Done when: the application context can map every entity (`ddl-auto` creates all five tables with their constraints).

## Phase 4: API contracts

`J/dto/request/TransactionRequestDto` with validation; `J/dto/response/*` records; `GlobalExceptionHandler` mapping domain and
request errors to the shared error body.

Done when: every error row in docs/API.md has a handler.

## Phase 5: Business logic

1. `TransactionProcessor`: one `@Transactional` method implementing the flow in docs/ARCHITECTURE.md section 4.1
   (fast-path idempotency, id resolution, ordered locking, re-check, ledger write, replay).
2. `TransactionService`: facade that recovers from duplicate-key races; read-only balance and history use-cases.

Done when: top-up, bonus and spend produce two ledger entries each and keep cached balances equal to the ledger.

## Phase 6: REST and seeding

1. `TransactionController`: three endpoints, 201/200 logic, both balance paths.
2. `config/DataSeeder`: idempotent seed through the real service.

Done when: `bootRun` starts, Aditi has 500 and Priya 300 Gold Coins, and Swagger UI lists the endpoints.

## Phase 7: Tests

1. Test setup: `application-test.yml`, `AbstractIntegrationTest`, context smoke test.
2. Unit tests.
3. Integration and controller tests.
4. Concurrency tests.

Done when: `./gradlew test` is green and the two ledger invariants hold after the concurrency tests.

## Phase 8: Containerisation

Multi-stage `Dockerfile` (JDK build, JRE runtime, non-root user), `.dockerignore`, `docker-compose.yml` with a health-checked
PostgreSQL 16.

Done when: `docker compose up --build` serves the API on 8080 and the seeded balances are readable.

## Final verification checklist

- [ ] `./gradlew clean test` passes
- [ ] `docker compose up --build` starts both containers; `GET /api/v1/balance?email=aditisingh@gmail.com&assetCode=GOLD_COINS` returns 500
- [ ] Top-up with a key, repeat it: second call is `200` with the same `transactionId`
- [ ] Spend more than the balance: `400 Insufficient balance`, balance unchanged
- [ ] Restart the app: seed data is not duplicated
- [ ] Audit queries in docs/DATABASE.md return the expected results
