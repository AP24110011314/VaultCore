# Architecture

## 1. Layers

```
Web & REST Controllers (UiController for Thymeleaf, TransactionController for REST API)
    │
TransactionService     (public business API; duplicate-key race recovery; read use-cases)
    │
TransactionProcessor   (THE database transaction: row locking, idempotency, ledger writes)
    │
Repositories           (Spring Data JPA; lock query; id-only lookups; zero-sum checks)
    │
PostgreSQL             (ACID, row locks, UNIQUE constraints)
```

Dependencies only point downwards:
- UI Layer calls `TransactionService` to render Thymeleaf views.
- REST Controllers call `TransactionService` with `@Valid` DTO requests and return DTO responses.
- `TransactionProcessor` executes inside `@Transactional` with pessimistic row-locking in deterministic ascending UUID order.
- Entities never escape the service layer; DTO records cross the controller boundary.

## 2. Package layout

Base package: `com.vaultcore.wallet`

| Package | Contents | Responsibility |
|---|---|---|
| (root) | `VaultCoreApplication` | Spring Boot entry point |
| `config` | `DataSeeder`, `OpenApiConfig` | Seeding reference data; OpenAPI 3.0 configuration |
| `controller` | `TransactionController`, `UiController` | REST endpoints and Thymeleaf server-rendered web UI |
| `dto.request` | `TransactionRequestDto` | Inbound payload and bean-validation rules |
| `dto.response` | `TransactionResponseDto`, `BalanceResponseDto`, `ListTransactionResponseDto`, `UserAssetBalanceResponseDto`, `AssetResponseDto`, `UserResponseDto`, `AuditReconciliationResponseDto`, `AccountReconciliationDto`, `TreasuryBalanceDto`, `ErrorResponseDto` | Immutable outbound payloads (Java records) |
| `enums` | `TransactionType`, `LedgerEntryType`, `AccountType`, `Status` | Closed domain enumerations |
| `exception` | `ResourceNotFoundException`, `InsufficientFundsException`, `GlobalExceptionHandler` | Domain exceptions and HTTP status mappings |
| `model` | `Asset`, `User`, `Account`, `Transaction`, `LedgerEntry` | JPA entities |
| `repository` | `AccountRepository`, `AssetRepository`, `UserRepository`, `TransactionRepository`, `LedgerEntryRepository` | Persistence, pessimistic row-locking queries, and audit aggregations |
| `service` | `TransactionService`, `TransactionProcessor`, `TransactionResult` | Core business transactions, lock-free audit, and idempotency recovery |

### Static Assets & Templates
- `src/main/resources/templates/`: Thymeleaf server-rendered templates (`layout.html`, `dashboard.html`, `transaction-new.html`, `history.html`, `audit.html`, `error/404.html`, `error/error.html`).
- `src/main/resources/static/css/`: Offline stylesheets with CSS Custom Properties (`vaultcore.css`, `swagger-custom.css`).
- `src/main/resources/static/js/`: Vanilla JavaScript for client-side theming, user memory, form submission, and idempotency key lifecycle (`vaultcore.js`).
- `src/main/resources/static/img/`: Vector branding (`vaultcore-logo.svg`, `vaultcore-icon.svg`, `favicon.svg`).

## 3. Domain model

- **Asset**: A virtual currency (`GOLD_COINS`, `DIAMONDS`, `LOYALTY_POINTS`). Unique code.
- **User**: Identified by unique email address.
- **Account**: One balance for one `(user, asset)` pair. Type is `USER` or `SYSTEM` (Treasury).
  `balance` is a cached sum. USER accounts can never go negative; SYSTEM accounts can.
- **Transaction**: One business operation with a unique `idempotencyKey`.
- **LedgerEntry**: Append-only movement (`DEBIT` or `CREDIT`) with `amount` and `balanceAfter`.

### Money movement rules

| Type | Debit | Credit | Extra rule |
|---|---|---|---|
| `TOPUP` | Treasury | User | none |
| `BONUS` | Treasury | User | none |
| `SPEND` | User | Treasury | user balance must be >= amount |

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

### 4.2 Web UI Flow & Browser Idempotency Lifecycle

1. **Dashboard (`/`)**: Thymeleaf renders user selector, asset balance cards, and recent transactions server-side.
2. **New Transaction (`/transactions/new`)**:
   - Form loads: Client generates a UUID idempotency key via `crypto.randomUUID()`.
   - Submission: Dispatches via browser `fetch` to `POST /api/v1/transactions`. Submit button is locked.
   - Network failure: Key is preserved; user can safely retry without duplicate side effects.
   - Definitive response (201, 200, 400, 404): Key is regenerated for subsequent operations.
3. **Audit (`/audit`)**: Performs a lock-free query checking `sum(balance) == 0` and verifying every cached balance against `sum(ledger_entries)`.

## 5. Key design decisions

1. **Server-rendered Thymeleaf + Vanilla Assets**: Fast, lightweight, offline-first. No external npm pipelines, webpack, or third-party CDN connections required.
2. **Double-entry ledger plus cached balance**: Reads are O(1); ledger entries are append-only source-of-truth.
3. **Pessimistic locking for every write type**: Locks accounts in ascending UUID order, eliminating deadlocks and preventing lost updates.
4. **Id-only lookups before locking**: Entities are loaded strictly through the locking query to avoid stale Hibernate first-level cache entries.
5. **Historical balance tracking**: `balance_after` stored on ledger entries guarantees idempotent replays return exact historical balances.
6. **Records for DTOs, no Lombok**: Pure Java 21 records for immutable API contracts.
