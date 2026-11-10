# Internal Wallet Service

A ledger-based wallet service for high-traffic applications such as gaming platforms or loyalty programmes.
It manages application-specific virtual credits (Gold Coins, Diamonds, Loyalty Points) with full transactional
integrity, concurrency safety and idempotent operations. Written entirely in Java.

| Document | What it covers |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Layers, packages, request flows, design decisions, differences from the original project |
| [docs/DATABASE.md](docs/DATABASE.md) | Tables, constraints, indexes, invariants, seed data |
| [docs/API.md](docs/API.md) | Endpoints, payloads, status codes, curl examples |
| [docs/CONCURRENCY_AND_IDEMPOTENCY.md](docs/CONCURRENCY_AND_IDEMPOTENCY.md) | Locking strategy, deadlock avoidance, duplicate-request handling |
| [docs/TESTING.md](docs/TESTING.md) | Test strategy and what each test proves |
| [docs/IMPLEMENTATION_PLAN.md](docs/IMPLEMENTATION_PLAN.md) | The build, phase by phase, with a definition of done per phase |
| [docs/COMMIT_PLAN.md](docs/COMMIT_PLAN.md) | Suggested commit sequence (2-3 word messages) with exact `git add` commands |

## Tech stack

| Technology | Why |
|---|---|
| Java 21, Spring Boot 4 | Declarative transactions (`@Transactional`) and Spring Data JPA keep the data-access code small. |
| PostgreSQL 16 | ACID transactions and row-level locking (`SELECT ... FOR UPDATE`) are the basis of the concurrency model. |
| Double-entry ledger | Every movement is one debit plus one credit, so the whole system always sums to zero and every balance is auditable. |
| Docker + Compose | `docker compose up --build` runs the app and its database with nothing installed locally. |
| H2 (tests only) | The test suite needs neither Docker nor a local PostgreSQL. |

## Quick start

### Option 1: Docker Compose

```bash
docker compose up --build
```

Starts PostgreSQL 16 and the application on port 8080. Tables are created by Hibernate and the reference data
is seeded by the application on startup.

- API base: `http://localhost:8080/api/v1`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

### Option 2: Run locally

Prerequisites: Java 21 and a PostgreSQL 16 database.

```bash
chmod +x gradlew      # macOS/Linux, once (not needed on Windows: use gradlew.bat)
createdb wallet
./gradlew bootRun
```

### Configuration

All values have defaults that match the Compose setup.

| Environment variable | Default | Purpose |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/wallet` | JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | Database user |
| `SPRING_DATASOURCE_PASSWORD` | `postgres` | Database password |
| `PORT` | `8080` | HTTP port (hosting platforms such as Render set this) |
| `WALLET_SEED_ENABLED` | `true` | Set `false` to skip seeding |

### Run the tests

```bash
./gradlew test
```

## Seed data

Created on startup by `DataSeeder` (idempotent, safe on every boot):

| Assets | Users | Opening balances |
|---|---|---|
| `GOLD_COINS` Gold Coins | Aditi Singh, `aditisingh@gmail.com` | Aditi: 500 Gold Coins |
| `DIAMONDS` Diamonds | Priya Jha, `priyajha@gmail.com` | Priya: 300 Gold Coins |
| `LOYALTY_POINTS` Loyalty Points | | Treasury: -800 Gold Coins (net outflow, expected for a system account) |

Each user also has an empty Diamonds account. One SYSTEM (Treasury) account exists per asset.

## API at a glance

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/transactions` | `TOPUP`, `BONUS` or `SPEND`. `201` when created, `200` when an idempotent replay. |
| GET | `/api/v1/balance?email=&assetCode=` | Current balance. Also served at `/api/v1/wallet/balance`. |
| GET | `/api/v1/transactions?email=` | Transaction history, newest first. |

```bash
curl -X POST http://localhost:8080/api/v1/transactions \
  -H 'Content-Type: application/json' \
  -d '{"type":"TOPUP","email":"aditisingh@gmail.com","assetCode":"GOLD_COINS","amount":100,"idempotencyKey":"demo-1"}'
```

Full details in [docs/API.md](docs/API.md).

## How correctness is guaranteed

- **Double entry.** Each transaction writes exactly two ledger entries (debit + credit) in one database transaction.
  `accounts.balance` is a cached read value; the ledger is the source of truth.
- **No lost updates, no overdrafts.** Both accounts involved in a movement are locked with `SELECT ... FOR UPDATE`,
  always in ascending id order, for every transaction type.
- **Safe retries.** Each request carries an `idempotencyKey`. A repeated key returns the original result and never
  moves money twice, even when duplicates arrive at the same instant (a database `UNIQUE` constraint is the final guard).

See [docs/CONCURRENCY_AND_IDEMPOTENCY.md](docs/CONCURRENCY_AND_IDEMPOTENCY.md).

## Known limitations

- Transaction history is not paginated.
- Schema is managed by Hibernate (`ddl-auto: update`). For a production rollout, introduce a migration tool such as Flyway.
- There is no authentication; the service is intended to sit behind an internal gateway.
- Every transaction for an asset locks that asset's single Treasury row, so throughput per asset is serialised.
  See the concurrency document for the trade-off and ways to scale it.
- `Status.FAILED` exists in the model but is never written: failed requests roll back completely and leave no row.
