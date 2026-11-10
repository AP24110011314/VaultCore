# Architecture

## 1. Layers

```
Controller   (REST, validation, HTTP status mapping)
    |
TransactionService   (public business API; recovers from duplicate-key races; read use-cases)
    |
TransactionProcessor (THE database transaction: locking, idempotency, ledger writes)
    |
Repositories         (Spring Data JPA; lock query; id-only lookups)
    |
PostgreSQL           (ACID, row locks, UNIQUE constraints)
```

Dependencies only point downwards. DTOs cross the controller boundary; entities never leave the service layer.

## 2. Package layout

Base package: `com.service.internalWallet`

| Package | Contents | Responsibility |
|---|---|---|
| (root) | `InternalWalletApplication` | Spring Boot entry point |
| `config` | `DataSeeder` | Idempotent startup seeding through the real ledger flow |
| `controller` | `TransactionController` | Endpoints, `@Valid`, 201 vs 200 |
| `dto.request` | `TransactionRequestDto` | Inbound payload and bean-validation rules |
| `dto.response` | `TransactionResponseDto`, `BalanceResponseDto`, `ListTransactionResponseDto`, `ErrorResponseDto` | Outbound payloads (Java records) |
| `enums` | `TransactionType`, `LedgerEntryType`, `AccountType`, `Status` | Closed value sets |
| `exception` | `ResourceNotFoundException`, `InsufficientFundsException`, `GlobalExceptionHandler` | Domain errors and their HTTP mapping |
| `model` | `Asset`, `User`, `Account`, `Transaction`, `LedgerEntry` | JPA entities |
| `repository` | one interface per entity | Persistence and the `FOR UPDATE` query |
| `service` | `TransactionService`, `TransactionProcessor`, `TransactionResult` | Business logic |

## 3. Domain model

- **Asset**: a virtual currency (`GOLD_COINS`, ...). `code` is unique.
- **User**: identified by unique `email`.
- **Account**: one balance for one (owner, asset). `type` is `USER` (owned by a user) or `SYSTEM` (the Treasury, no user).
  `balance` is a cached value. USER accounts can never go negative; SYSTEM accounts can.
- **Transaction**: one business operation with a unique `idempotencyKey`.
- **LedgerEntry**: one side of a movement (`DEBIT` or `CREDIT`), with `amount` and `balanceAfter`.

### Money movement rules

| Type | Debit | Credit | Extra rule |
|---|---|---|---|
| `TOPUP` | Treasury | User | none |
| `BONUS` | Treasury | User | none |
| `SPEND` | User | Treasury | user balance must be >= amount |

`TOPUP` and `BONUS` move money identically; the type is recorded so reporting can tell purchases from promotional grants.

## 4. Request flows

### 4.1 POST /api/v1/transactions

```
Controller
  @Valid body -> 400 on bad input
  TransactionService.processTransaction
    TransactionProcessor.process            (one DB transaction begins)
      1. find transaction by idempotencyKey ----found----> replay stored result (200)
      2. load user, asset            -> 404 if missing
         resolve user-account id     -> 404 "Account not found"
         resolve treasury-account id -> 404 "System Account not found"
      3. SELECT ... FOR UPDATE on both accounts, lower id first
      4. find transaction by idempotencyKey again ----found----> replay (200)
      5. SPEND and balance < amount -> InsufficientFundsException (400, rolls back)
      6. INSERT transaction (flushed immediately)
         debit account, credit account (cached balances)
         INSERT two ledger entries (with balanceAfter)
      commit                                (locks released)
    on DataIntegrityViolationException (duplicate key lost a race):
      look the key up again in a new read-only transaction and replay it
  201 Created (new) or 200 OK (replay)
```

### 4.2 GET balance

Resolve user, asset, account; return the cached `balance`. No locks (read-committed snapshot).

### 4.3 GET history

One query: transactions that have at least one ledger entry on an account owned by the user's email,
ordered by `created_at DESC`.

## 5. Key design decisions

1. **Double-entry ledger plus cached balance.** Reads are O(1); the ledger lets us prove the cache is right
   (`sum(ledger for account) == account.balance`, and `sum(all balances) == 0`).
2. **Pessimistic locking for every write type.** Wallet traffic is write-heavy on hot rows; blocking briefly is cheaper
   and simpler than optimistic retry storms. See the concurrency document.
3. **Two beans for writes.** `TransactionProcessor` owns the database transaction; `TransactionService` wraps it.
   After a constraint violation Spring marks the transaction rollback-only, so recovery (replaying the winner's result)
   has to happen outside it.
4. **Id-only lookups before locking.** The write path never loads an `Account` entity until it holds the lock, so the
   persistence context can never contain a stale pre-lock copy of a balance.
5. **`balance_after` on ledger entries.** Lets an idempotent replay return the original response exactly, instead
   of today's balance.
6. **Records for DTOs, plain classes for entities, no Lombok.** Less magic, nothing to configure in the IDE.
7. **Java-only seeding.** `DataSeeder` replaces a SQL script; opening balances are real transactions, so the seed
   data is double-entry too.

## 6. Differences from the original project

The original (a Spring Boot project of the same name) was analysed and rebuilt from scratch. The public behaviour is kept;
these points deliberately differ, mostly to fix correctness gaps:

| Area | Original | This implementation |
|---|---|---|
| Locking | Only `SPEND` locked rows; `TOPUP`/`BONUS` updated balances without locks (lost updates possible under concurrency) | All three types lock both accounts in id order |
| Lock correctness | Account entities were loaded before the lock query, so the lock call could return the cached pre-lock state | Ids are resolved first; entities are loaded only through the locking query |
| Idempotency constraint | README claims `UNIQUE(idempotency_key)`, but `seed.sql` did not create it | Constraint is declared on the entity (and `NOT NULL`) |
| Duplicate race | Unhandled (would surface as a 500) | Re-check under lock, plus recovery in `TransactionService` |
| Replay response | `balanceAfter` was set to the amount | True historical balance via `ledger_entries.balance_after` |
| Replay status | Always `201` | `200` for replays, `201` for new transactions |
| Balance path | Code: `/api/v1/balance`; README: `/api/v1/wallet/balance` | Both paths work |
| Balance response | Code: `userId, assetName, balance`; README: `email, assetName, balance` | Superset: `userId, email, assetCode, assetName, balance` |
| Timestamps | `LocalDateTime` (README showed `Z`) | `Instant`, stored as `TIMESTAMPTZ`, serialised in UTC |
| Error body | Field `localDateTime` | Field `timestamp` |
| Validation errors | Spring default body | `{"error":"Validation Failed", ...}` with per-field messages |
| Schema and seed | `seed.sql` (DROPs tables, runs only via Compose) | Hibernate-created schema; `DataSeeder` in Java |
| Extra constraints | None in SQL | Unique email, asset code, (user, asset) pair; `NOT NULL` on all business columns |
| Build | `devtools` on the runtime classpath; Compose forced `linux/arm64` | `devtools` is `developmentOnly`; no platform pin |
| Tests | One `contextLoads` test | Unit, integration, controller and concurrency tests |
