# Testing

## Running

```bash
# Run all 55 tests (Unit, Integration, Web, UI, Concurrency)
./gradlew test

# Run only UI tests
./gradlew test --tests '*UiControllerTest'

# Run only REST API controller tests
./gradlew test --tests '*TransactionControllerTest'

# Run only concurrency contention tests
./gradlew test --tests '*ConcurrencyIntegrationTest'
```

No Docker or PostgreSQL needed: integration tests use an in-memory H2 database (`application-test.yml`, profile `test`).
The suite shares one Spring context; every test creates its own random user, so tests are independent and can run in any order.
Only `DataSeederTest` reads the seeded users (Aditi, Priya), and no other test may modify them.

## Layers

| Layer | Class | Proves |
|---|---|---|
| Unit | `AccountTest` | Credit/debit arithmetic, USER accounts cannot be overdrawn, SYSTEM accounts can go negative, overflow detected |
| Unit | `TransactionRequestDtoValidationTest` | Every bean-validation rule on the request |
| Integration | `TransactionServiceIntegrationTest` | Top-up, bonus, spend; exact-balance spend; insufficient funds leaves no trace; double-entry balance; replay returns original result; 404 cases; per-asset balances; paginated history ordering and scoping; reconciliation audit |
| Integration | `DataSeederTest` | Seeded assets, users and balances; ledger backs opening balances; re-running seeder changes nothing |
| Web API | `TransactionControllerTest` | Status codes (201/200/400/404), error bodies, both balance paths, assets, users, balances, paginated history returning plain JSON array with additive fields, reconciliation audit |
| Web UI | `UiControllerTest` | Server-rendered pages (`/`, `/transactions/new`, `/history`, `/audit`), status codes 200, model attributes, and rendered brand keywords ("VaultCore", etc.) |
| Concurrency | `ConcurrencyIntegrationTest` | No overdraft, no lost update, exactly-once for a shared idempotency key, no deadlock |
| Smoke | `VaultCoreApplicationTests` | The context starts (wiring, entity mapping, seeder) and initial system balances to zero |

## Invariant helpers (`AbstractIntegrationTest`)

- `assertLedgerMatchesBalance(accountId)`: Cached balance equals the net of the account's ledger entries.
- `assertSystemBalancesToZero()`: The sum of all account balances is 0.

Both are asserted after the money-moving and concurrency tests.

## Notes

- H2 supports `SELECT ... FOR UPDATE` row locking, so the concurrency tests exercise the same locking paths as PostgreSQL.
- `LOCK_TIMEOUT=15000` in the H2 URL keeps contention tests from failing on H2's short default timeout; a lock timeout or deadlock surfaces as an unexpected exception and fails the test.
- Ordering assertions for history put a few milliseconds between transactions so timestamps cannot tie.
- UI tests use Spring MockMvc with the real Thymeleaf view resolver to verify template parsing and attribute binding.
