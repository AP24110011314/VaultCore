# Testing

## Running

```bash
./gradlew test                                    # everything
./gradlew test --tests '*ConcurrencyIntegrationTest'
```

No Docker or PostgreSQL needed: integration tests use an in-memory H2 database (`application-test.yml`, profile `test`).
The suite shares one Spring context; every test creates its own random user, so tests are independent and can run in any order.
Only `DataSeederTest` reads the seeded users (Aditi, Priya), and no other test may modify them.

## Layers

| Layer | Class | Proves |
|---|---|---|
| Unit | `AccountTest` | credit/debit arithmetic, USER accounts cannot be overdrawn, SYSTEM accounts can go negative, overflow detected |
| Unit | `TransactionRequestDtoValidationTest` | every bean-validation rule on the request |
| Integration | `TransactionServiceIntegrationTest` | top-up, bonus, spend; exact-balance spend; insufficient funds leaves no trace; double-entry balance; replay returns the original result; 404 cases; per-asset balances; history ordering and scoping |
| Integration | `DataSeederTest` | seeded assets, users and balances; ledger backs the opening balances; re-running the seeder changes nothing |
| Web | `TransactionControllerTest` | status codes (201/200/400/404), error bodies, both balance paths, history, validation messages |
| Concurrency | `ConcurrencyIntegrationTest` | no overdraft, no lost update, exactly-once for a shared idempotency key, no deadlock |
| Smoke | `InternalWalletApplicationTests` | the context starts (wiring, entity mapping, seeder) |

## Invariant helpers (`AbstractIntegrationTest`)

- `assertLedgerMatchesBalance(accountId)`: cached balance equals the net of the account's ledger entries.
- `assertSystemBalancesToZero()`: the sum of all account balances is 0.

Both are asserted after the money-moving and concurrency tests.

## Notes

- H2 supports `SELECT ... FOR UPDATE` row locking, so the concurrency tests exercise the same locking paths as PostgreSQL.
  Running the same suite against PostgreSQL (for example with Testcontainers) is a worthwhile extra step before production.
- `LOCK_TIMEOUT=15000` in the H2 URL keeps contention tests from failing on H2's short default timeout; a lock timeout
  or deadlock surfaces as an unexpected exception and fails the test.
- Ordering assertions for history put a few milliseconds between transactions so timestamps cannot tie.
