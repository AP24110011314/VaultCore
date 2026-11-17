# API Reference

Base URL: `http://localhost:8080/api/v1`. All bodies are JSON. Interactive docs: `/swagger-ui.html`, raw spec: `/v3/api-docs`.

## 1. Create a transaction

`POST /api/v1/transactions`

One endpoint for all three flows.

| Type | Effect |
|---|---|
| `TOPUP` | User buys credits: Treasury is debited, user is credited |
| `BONUS` | Promotional grant: same movement as `TOPUP`, recorded separately |
| `SPEND` | User spends credits: user is debited, Treasury is credited; needs sufficient balance |

### Request

```json
{
  "type": "TOPUP",
  "email": "aditisingh@gmail.com",
  "assetCode": "GOLD_COINS",
  "amount": 100,
  "idempotencyKey": "unique-key-123"
}
```

| Field | Type | Required | Rules |
|---|---|---|---|
| type | string | yes | `TOPUP`, `BONUS` or `SPEND` |
| email | string | yes | valid email of an existing user |
| assetCode | string | yes | code of an existing asset, e.g. `GOLD_COINS` |
| amount | integer (long) | yes | greater than 0 |
| idempotencyKey | string | yes | 1 to 100 characters, unique per logical operation |

### Response: 201 Created (new) or 200 OK (replay of an earlier request with the same key)

```json
{
  "transactionId": "550e8400-e29b-41d4-a716-446655440000",
  "transactionType": "TOPUP",
  "amount": 100,
  "balanceAfter": 600,
  "status": "SUCCESS",
  "createdAt": "2026-02-22T12:00:00.123456Z"
}
```

`balanceAfter` is the user's balance right after this transaction. On a replay it is the historical value, not today's.

## 2. Get balance (Single Asset)

`GET /api/v1/balance?email={email}&assetCode={assetCode}`

Also available as `GET /api/v1/wallet/balance`.

```json
{
  "userId": "aaaaaaaa-0000-0000-0000-000000000001",
  "email": "aditisingh@gmail.com",
  "assetCode": "GOLD_COINS",
  "assetName": "Gold Coins",
  "balance": 600
}
```

## 3. Get all balances for a user

`GET /api/v1/balances?email={email}`

Returns the user's balances across all supported assets in the ledger. Returns `404 Not Found` if the user does not exist.

```json
[
  {
    "assetCode": "GOLD_COINS",
    "assetName": "Gold Coins",
    "balance": 600
  },
  {
    "assetCode": "DIAMONDS",
    "assetName": "Diamonds",
    "balance": 0
  },
  {
    "assetCode": "LOYALTY_POINTS",
    "assetName": "Loyalty Points",
    "balance": 0
  }
]
```

## 4. List all assets

`GET /api/v1/assets`

Returns all active virtual currency assets configured in the ledger.

```json
[
  { "code": "DIAMONDS", "name": "Diamonds" },
  { "code": "GOLD_COINS", "name": "Gold Coins" },
  { "code": "LOYALTY_POINTS", "name": "Loyalty Points" }
]
```

## 5. List users

`GET /api/v1/users`

> ⚠️ **Note**: This endpoint is intended for internal services and the UI selector. In a public-facing deployment, ensure proper authentication and role-based authorization before exposing user directories.

```json
[
  {
    "userId": "aaaaaaaa-0000-0000-0000-000000000001",
    "name": "Aditi Singh",
    "email": "aditisingh@gmail.com"
  },
  {
    "userId": "aaaaaaaa-0000-0000-0000-000000000002",
    "name": "Priya Jha",
    "email": "priyajha@gmail.com"
  }
]
```

## 6. Get transaction history

`GET /api/v1/transactions?email={email}&page={page}&size={size}`

Returns transactions that touched the user's accounts, ordered newest first.
- Optional query parameters: `page` (default `0`, 0-based), `size` (default `50`, max `100`).
- Returns a plain JSON array by default for 100% backward compatibility. When pagination params are passed, `X-Total-Count`, `X-Page-Number`, and `X-Page-Size` HTTP response headers are provided.
- Response items include additive `amount` and `assetCode` fields.

```json
[
  {
    "transactionId": "550e8400-e29b-41d4-a716-446655440000",
    "transactionType": "TOPUP",
    "amount": 100,
    "assetCode": "GOLD_COINS",
    "status": "SUCCESS",
    "createdAt": "2026-02-22T12:00:00.123456Z"
  }
]
```

## 7. Ledger integrity & reconciliation audit

`GET /api/v1/audit/reconciliation`

Performs an instantaneous, read-only reconciliation across the entire ledger:
- Verifies system invariant: `∑ all account balances == 0`.
- Verifies every account's cached balance against its append-only ledger entries sum (`∑ credits - ∑ debits`).
- Returns Treasury balances for each asset.

```json
{
  "systemTotalBalance": 0,
  "systemBalancesToZero": true,
  "treasuryBalances": [
    { "assetCode": "GOLD_COINS", "assetName": "Gold Coins", "balance": -800 },
    { "assetCode": "DIAMONDS", "assetName": "Diamonds", "balance": 0 },
    { "assetCode": "LOYALTY_POINTS", "assetName": "Loyalty Points", "balance": 0 }
  ],
  "accounts": [
    {
      "accountId": "aaaaaaaa-0000-0000-0000-000000000001",
      "accountType": "USER",
      "owner": "aditisingh@gmail.com",
      "assetCode": "GOLD_COINS",
      "assetName": "Gold Coins",
      "cachedBalance": 500,
      "ledgerBalance": 500,
      "reconciled": true
    }
  ],
  "allReconciled": true
}
```

## 8. Errors

All errors share one shape:

```json
{ "error": "Not Found", "message": "User not found", "timestamp": "2026-02-22T12:00:00.123456Z" }
```

| HTTP | `error` | When | Example `message` |
|---|---|---|---|
| 400 | `Validation Failed` | A body field is missing or invalid | `amount: Amount must be greater than 0; email: Email is required` |
| 400 | `Invalid Data` | Insufficient balance | `Insufficient balance` |
| 400 | `Invalid Data` | Unreadable JSON or unknown `type` value | `Malformed request body (check JSON syntax and enum values such as type)` |
| 400 | `Invalid Data` | Missing query parameter | `Required parameter 'assetCode' is missing` |
| 400 | `Invalid Data` | Balance would overflow `long` | `Amount would overflow the balance` |
| 404 | `Not Found` | Unknown user / asset / account | `User not found`, `Asset not found`, `Account not found`, `System Account not found` |

A rejected request (400 or 404) never leaves partial data behind; its idempotency key is NOT consumed, so the client may
retry with the same key after fixing the cause (for example after topping up).

## 9. Examples

```bash
# Top up 100 Gold Coins
curl -X POST http://localhost:8080/api/v1/transactions -H 'Content-Type: application/json' \
  -d '{"type":"TOPUP","email":"aditisingh@gmail.com","assetCode":"GOLD_COINS","amount":100,"idempotencyKey":"k-1"}'

# Replay: 200 OK, same transactionId, balance unchanged
curl -i -X POST http://localhost:8080/api/v1/transactions -H 'Content-Type: application/json' \
  -d '{"type":"TOPUP","email":"aditisingh@gmail.com","assetCode":"GOLD_COINS","amount":100,"idempotencyKey":"k-1"}'

# Check balances across all assets
curl http://localhost:8080/api/v1/balances?email=aditisingh@gmail.com

# Fetch ledger audit reconciliation
curl http://localhost:8080/api/v1/audit/reconciliation
```
