# Concurrency and Idempotency

## 1. The problem

Two requests spend from the same wallet at the same time:

```
balance = 100
A reads 100, B reads 100
A checks 100 >= 80 OK, B checks 100 >= 80 OK
A debits 80, B debits 80   ->  balance = -60
```

The same race with two top-ups silently loses one of them (both read 100, both write 150 instead of 200).
A client retrying after a timeout can also apply the same top-up twice.

## 2. Pessimistic row locking

Before changing balances, the processor locks the rows it will modify:

```sql
SELECT ... FROM accounts WHERE id = ? FOR UPDATE   -- JPA: @Lock(PESSIMISTIC_WRITE)
```

A second transaction that wants the same row waits until the first commits or rolls back, then reads the updated value.

```
Tx 1: BEGIN -> lock user + treasury -> check balance -> write -> COMMIT
Tx 2: BEGIN -> lock user (waits...) ............................-> lock acquired -> sees new balance
```

This applies to **all three transaction types**. Top-ups need it too: without the lock, two concurrent top-ups lose an update.

### Why pessimistic rather than optimistic

Wallet accounts are hot rows with write-heavy traffic. Optimistic locking would turn contention into retries, and under
heavy load into retry storms. Waiting for a lock is cheaper and gives a simple guarantee: at most one writer per account at a time.

## 3. Deadlock avoidance: consistent lock order

Every transaction locks two accounts (user and Treasury). If one request locked (user, treasury) while another locked
(treasury, user), each could hold one lock and wait for the other.

Rule: **always lock the account with the smaller UUID first**, whatever the transaction direction. With one global order there
is no circular wait, so no deadlock. Implemented in `TransactionProcessor.lockInOrder`.

## 4. Never read the balance before the lock

The write path first resolves account **ids only** (`select a.id ...`), and loads `Account` entities exclusively through
the `FOR UPDATE` query. If an entity were loaded earlier in the same persistence context, the lock query could return that
cached, pre-lock state and the balance check would use stale data. Id-only lookups make that impossible.

## 5. Idempotency

Every write carries a client-generated `idempotencyKey`.

```
fast path : key already stored?  -> return stored result (no locks)
lock both accounts
re-check  : key stored meanwhile? -> return stored result
process   : insert transaction, ledger entries, update balances
```

1. **Fast path.** Retries of an already-completed request are answered without touching locks.
2. **Re-check under the lock.** Two identical requests for the same user and asset serialise on the account lock. The second
   one waits, then sees the first one's committed transaction and replays it instead of applying it again.
3. **Database constraint.** `transactions.idempotency_key` is `UNIQUE`. If the same key is sent for different accounts, so that
   the lock does not serialise them, the second insert fails with `DataIntegrityViolationException`.
   `TransactionService` catches it and returns the winner's stored result (an inner `try/catch` is not possible because the
   failed statement leaves the transaction rollback-only; this is why the transactional core is a separate bean).

### Replay returns the original response

The replay is rebuilt from the user's ledger entry: the `amount` and `balanceAfter` are what they were when the transaction
ran, even if the wallet has changed since. The HTTP status is `200` for replays and `201` for new transactions.

### What is not idempotent

A request that fails (insufficient funds, unknown user) rolls back completely and stores nothing, so its key is free to
be retried.

## 6. Throughput trade-off

Every transaction of an asset updates that asset's single Treasury account, so those transactions are serialised on one row.
This is correct and simple, and fine for a service of this scale. Options if it ever becomes the bottleneck:

- Shard the Treasury: several SYSTEM accounts per asset, chosen by `hash(user) % N`.
- Stop updating the Treasury's cached balance inline and derive it from the ledger asynchronously
  (the ledger entries remain the source of truth).

## 7. How the tests prove it

See `ConcurrencyIntegrationTest` (docs/TESTING.md): 20 concurrent spends against a balance of 100 allow exactly 10;
20 concurrent top-ups lose no update; 10 concurrent requests with the same key create exactly one transaction;
40 mixed operations over 4 users finish without deadlock and every balance reconciles with the ledger.
