package com.service.internalWallet.service;

import com.service.internalWallet.dto.request.TransactionRequestDto;
import com.service.internalWallet.dto.response.TransactionResponseDto;
import com.service.internalWallet.enums.AccountType;
import com.service.internalWallet.enums.LedgerEntryType;
import com.service.internalWallet.enums.Status;
import com.service.internalWallet.enums.TransactionType;
import com.service.internalWallet.exception.ResourceNotFoundException;
import com.service.internalWallet.model.Account;
import com.service.internalWallet.model.Asset;
import com.service.internalWallet.model.LedgerEntry;
import com.service.internalWallet.model.Transaction;
import com.service.internalWallet.model.User;
import com.service.internalWallet.repository.AccountRepository;
import com.service.internalWallet.repository.AssetRepository;
import com.service.internalWallet.repository.LedgerEntryRepository;
import com.service.internalWallet.repository.TransactionRepository;
import com.service.internalWallet.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TransactionProcessor {

    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final AccountRepository accountRepository;

    public TransactionProcessor(TransactionRepository transactionRepository,
                                LedgerEntryRepository ledgerEntryRepository,
                                UserRepository userRepository,
                                AssetRepository assetRepository,
                                AccountRepository accountRepository) {
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.userRepository = userRepository;
        this.assetRepository = assetRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional
    public TransactionResult process(TransactionRequestDto request) {
        // 1. Fast path: find transaction by idempotencyKey
        Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            return replay(existing.get());
        }

        // 2. Load user and asset; resolve account IDs only
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Asset asset = assetRepository.findByCode(request.assetCode())
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found"));

        UUID userAccountId = accountRepository.findIdByUserIdAndAssetId(user.getId(), asset.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        UUID systemAccountId = accountRepository.findIdByTypeAndAssetId(AccountType.SYSTEM, asset.getId())
                .orElseThrow(() -> new ResourceNotFoundException("System Account not found"));

        // 3. SELECT ... FOR UPDATE on both accounts, smaller UUID first
        Account firstLocked;
        Account secondLocked;
        if (userAccountId.compareTo(systemAccountId) < 0) {
            firstLocked = accountRepository.findByIdForUpdate(userAccountId)
                    .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
            secondLocked = accountRepository.findByIdForUpdate(systemAccountId)
                    .orElseThrow(() -> new ResourceNotFoundException("System Account not found"));
        } else {
            firstLocked = accountRepository.findByIdForUpdate(systemAccountId)
                    .orElseThrow(() -> new ResourceNotFoundException("System Account not found"));
            secondLocked = accountRepository.findByIdForUpdate(userAccountId)
                    .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        }

        Account userAccount = userAccountId.equals(firstLocked.getId()) ? firstLocked : secondLocked;
        Account systemAccount = systemAccountId.equals(firstLocked.getId()) ? firstLocked : secondLocked;

        // 4. Re-check idempotency under lock
        Optional<Transaction> txUnderLock = transactionRepository.findByIdempotencyKey(request.idempotencyKey());
        if (txUnderLock.isPresent()) {
            return replay(txUnderLock.get());
        }

        // 5. Debit and credit according to transaction type
        if (request.type() == TransactionType.SPEND) {
            userAccount.debit(request.amount());
            systemAccount.credit(request.amount());
        } else if (request.type() == TransactionType.TOPUP || request.type() == TransactionType.BONUS) {
            systemAccount.debit(request.amount());
            userAccount.credit(request.amount());
        }

        // 6. Insert transaction (flushed immediately) and ledger entries
        Transaction transaction = new Transaction();
        transaction.setType(request.type());
        transaction.setIdempotencyKey(request.idempotencyKey());
        transaction.setStatus(Status.SUCCESS);
        transaction = transactionRepository.saveAndFlush(transaction);

        LedgerEntry userEntry = new LedgerEntry();
        userEntry.setTransaction(transaction);
        userEntry.setAccount(userAccount);
        userEntry.setType(request.type() == TransactionType.SPEND ? LedgerEntryType.DEBIT : LedgerEntryType.CREDIT);
        userEntry.setAmount(request.amount());
        userEntry.setBalanceAfter(userAccount.getBalance());

        LedgerEntry systemEntry = new LedgerEntry();
        systemEntry.setTransaction(transaction);
        systemEntry.setAccount(systemAccount);
        systemEntry.setType(request.type() == TransactionType.SPEND ? LedgerEntryType.CREDIT : LedgerEntryType.DEBIT);
        systemEntry.setAmount(request.amount());
        systemEntry.setBalanceAfter(systemAccount.getBalance());

        ledgerEntryRepository.saveAll(List.of(userEntry, systemEntry));
        accountRepository.saveAll(List.of(userAccount, systemAccount));

        TransactionResponseDto response = new TransactionResponseDto(
                transaction.getId(),
                transaction.getType(),
                request.amount(),
                userAccount.getBalance(),
                transaction.getStatus(),
                transaction.getCreatedAt()
        );
        return new TransactionResult(response, false);
    }

    public TransactionResult replay(Transaction transaction) {
        List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionId(transaction.getId());
        LedgerEntry userEntry = entries.stream()
                .filter(e -> e.getAccount().getType() == AccountType.USER)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Transaction has no user ledger entry"));

        TransactionResponseDto response = new TransactionResponseDto(
                transaction.getId(),
                transaction.getType(),
                userEntry.getAmount(),
                userEntry.getBalanceAfter(),
                transaction.getStatus(),
                transaction.getCreatedAt()
        );
        return new TransactionResult(response, true);
    }
}
