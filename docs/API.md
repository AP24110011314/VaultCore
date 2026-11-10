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

## 2. Get balance

`GET /api/v1/balance?email={email}&assetCode={assetCode}`

Also available as `GET /api/v1/wallet/balance` (the path used in the original README).

```json
{
  "userId": "aaaaaaaa-0000-0000-0000-000000000001",
  "email": "aditisingh@gmail.com",
  "assetCode": "GOLD_COINS",
  "assetName": "Gold Coins",
  "balance": 600
}
```

## 3. Get transaction history

`GET /api/v1/transactions?email={email}`

Returns every transaction that touched one of the user's accounts, newest first. An unknown email returns `[]`.

```json
[
  {
    "transactionId": "550e8400-e29b-41d4-a716-446655440000",
    "transactionType": "SPEND",
    "status": "SUCCESS",
    "createdAt": "2026-02-22T12:05:00.000000Z"
  }
]
```

## 4. Errors

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

## 5. Examples

```bash
# Top up 100 Gold Coins
curl -X POST localhost:8080/api/v1/transactions -H 'Content-Type: application/json' \
  -d '{"type":"TOPUP","email":"aditisingh@gmail.com","assetCode":"GOLD_COINS","amount":100,"idempotencyKey":"k-1"}'

# Same request again: 200 OK, same transactionId, balance unchanged
curl -i -X POST localhost:8080/api/v1/transactions -H 'Content-Type: application/json' \
  -d '{"type":"TOPUP","email":"aditisingh@gmail.com","assetCode":"GOLD_COINS","amount":100,"idempotencyKey":"k-1"}'

# Spend 80
curl -X POST localhost:8080/api/v1/transactions -H 'Content-Type: application/json' \
  -d '{"type":"SPEND","email":"aditisingh@gmail.com","assetCode":"GOLD_COINS","amount":80,"idempotencyKey":"k-2"}'

# Balance and history
curl 'localhost:8080/api/v1/balance?email=aditisingh@gmail.com&assetCode=GOLD_COINS'
curl 'localhost:8080/api/v1/transactions?email=aditisingh@gmail.com'
```

## 6. Client guidance

- Generate one idempotency key per logical operation (a UUID is fine) and reuse it on every retry of that operation.
- Treat `200` and `201` both as success.
- Reusing a key for a *different* operation returns the first operation's result; the payload is not compared.
