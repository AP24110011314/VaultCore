package com.vaultcore.wallet.service;

import com.vaultcore.wallet.dto.request.TransactionRequestDto;
import com.vaultcore.wallet.dto.response.AccountReconciliationDto;
import com.vaultcore.wallet.dto.response.AssetResponseDto;
import com.vaultcore.wallet.dto.response.AuditReconciliationResponseDto;
import com.vaultcore.wallet.dto.response.BalanceResponseDto;
import com.vaultcore.wallet.dto.response.ListTransactionResponseDto;
import com.vaultcore.wallet.dto.response.TreasuryBalanceDto;
import com.vaultcore.wallet.dto.response.UserAssetBalanceResponseDto;
import com.vaultcore.wallet.dto.response.UserResponseDto;
import com.vaultcore.wallet.enums.AccountType;
import com.vaultcore.wallet.exception.ResourceNotFoundException;
import com.vaultcore.wallet.model.Account;
import com.vaultcore.wallet.model.Asset;
import com.vaultcore.wallet.model.LedgerEntry;
import com.vaultcore.wallet.model.Transaction;
import com.vaultcore.wallet.model.User;
import com.vaultcore.wallet.repository.AccountRepository;
import com.vaultcore.wallet.repository.AssetRepository;
import com.vaultcore.wallet.repository.LedgerEntryRepository;
import com.vaultcore.wallet.repository.TransactionRepository;
import com.vaultcore.wallet.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private final TransactionProcessor transactionProcessor;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public TransactionService(TransactionProcessor transactionProcessor,
                              TransactionRepository transactionRepository,
                              UserRepository userRepository,
                              AssetRepository assetRepository,
                              AccountRepository accountRepository,
                              LedgerEntryRepository ledgerEntryRepository) {
        this.transactionProcessor = transactionProcessor;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.assetRepository = assetRepository;
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    public TransactionResult processTransaction(TransactionRequestDto request) {
        try {
            return transactionProcessor.process(request);
        } catch (DataIntegrityViolationException ex) {
            return replayAfterRace(request.idempotencyKey(), ex);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public TransactionResult replayAfterRace(String idempotencyKey, DataIntegrityViolationException originalException) {
        return transactionRepository.findByIdempotencyKey(idempotencyKey)
                .map(transactionProcessor::replay)
                .orElseThrow(() -> originalException);
    }

    @Transactional(readOnly = true)
    public BalanceResponseDto getBalance(String email, String assetCode) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Asset asset = assetRepository.findByCode(assetCode)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found"));

        Account account = accountRepository.findByUserIdAndAssetId(user.getId(), asset.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        return new BalanceResponseDto(
                user.getId(),
                user.getEmail(),
                asset.getCode(),
                asset.getName(),
                account.getBalance()
        );
    }

    @Transactional(readOnly = true)
    public List<UserAssetBalanceResponseDto> getBalances(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<Asset> allAssets = assetRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
        List<Account> accounts = accountRepository.findByUserId(user.getId());
        Map<UUID, Long> balanceByAssetId = accounts.stream()
                .collect(Collectors.toMap(a -> a.getAsset().getId(), Account::getBalance, (b1, b2) -> b1));

        return allAssets.stream()
                .map(asset -> new UserAssetBalanceResponseDto(
                        asset.getCode(),
                        asset.getName(),
                        balanceByAssetId.getOrDefault(asset.getId(), 0L)
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AssetResponseDto> getAllAssets() {
        return assetRepository.findAll(Sort.by(Sort.Direction.ASC, "name")).stream()
                .map(a -> new AssetResponseDto(a.getCode(), a.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserResponseDto> getAllUsers() {
        return userRepository.findAll(Sort.by(Sort.Direction.ASC, "name")).stream()
                .map(u -> new UserResponseDto(u.getId(), u.getName(), u.getEmail()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ListTransactionResponseDto> getTransactionHistory(String email) {
        return getTransactionHistory(email, 0, 50, false);
    }

    @Transactional(readOnly = true)
    public List<ListTransactionResponseDto> getTransactionHistory(String email, int page, int size) {
        return getTransactionHistory(email, page, size, true);
    }

    private List<ListTransactionResponseDto> getTransactionHistory(String email, int page, int size, boolean paginated) {
        List<Transaction> transactions;
        if (paginated) {
            int sanitizedPage = Math.max(0, page);
            int sanitizedSize = Math.min(100, Math.max(1, size));
            transactions = transactionRepository.findByUserEmail(email, PageRequest.of(sanitizedPage, sanitizedSize));
        } else {
            transactions = transactionRepository.findByUserEmail(email);
        }

        return transactions.stream()
                .map(t -> {
                    LedgerEntry userEntry = t.getLedgerEntries().stream()
                            .filter(le -> le.getAccount() != null && le.getAccount().getUser() != null
                                    && email.equalsIgnoreCase(le.getAccount().getUser().getEmail()))
                            .findFirst()
                            .orElse(null);

                    Long amount = null;
                    String assetCode = null;
                    if (userEntry != null) {
                        amount = userEntry.getAmount();
                        if (userEntry.getAccount().getAsset() != null) {
                            assetCode = userEntry.getAccount().getAsset().getCode();
                        }
                    } else if (!t.getLedgerEntries().isEmpty()) {
                        LedgerEntry first = t.getLedgerEntries().iterator().next();
                        amount = first.getAmount();
                        if (first.getAccount() != null && first.getAccount().getAsset() != null) {
                            assetCode = first.getAccount().getAsset().getCode();
                        }
                    }

                    return new ListTransactionResponseDto(
                            t.getId(),
                            t.getType(),
                            amount,
                            assetCode,
                            t.getStatus(),
                            t.getCreatedAt()
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public long countTransactions(String email) {
        return transactionRepository.countByUserEmail(email);
    }

    @Transactional(readOnly = true)
    public AuditReconciliationResponseDto getReconciliationAudit() {
        long systemTotal = accountRepository.sumAllBalances();
        boolean systemBalancesToZero = (systemTotal == 0);

        List<Account> allAccounts = accountRepository.findAll();
        List<AccountReconciliationDto> accountResults = new ArrayList<>();
        boolean allAccountsMatch = true;

        for (Account acc : allAccounts) {
            Long ledgerSum = ledgerEntryRepository.sumAmountsByAccountId(acc.getId());
            long ledgerBalance = ledgerSum != null ? ledgerSum : 0L;
            boolean matches = (acc.getBalance() == ledgerBalance);
            if (!matches) {
                allAccountsMatch = false;
            }

            String owner = (acc.getUser() != null) ? acc.getUser().getEmail() : "Treasury";
            String assetCode = (acc.getAsset() != null) ? acc.getAsset().getCode() : "";
            String assetName = (acc.getAsset() != null) ? acc.getAsset().getName() : "";

            accountResults.add(new AccountReconciliationDto(
                    acc.getId(),
                    acc.getType(),
                    owner,
                    assetCode,
                    assetName,
                    acc.getBalance(),
                    ledgerBalance,
                    matches
            ));
        }

        List<TreasuryBalanceDto> treasuryBalances = allAccounts.stream()
                .filter(a -> a.getType() == AccountType.SYSTEM)
                .map(a -> new TreasuryBalanceDto(
                        a.getAsset().getCode(),
                        a.getAsset().getName(),
                        a.getBalance()
                ))
                .toList();

        boolean overallReconciled = systemBalancesToZero && allAccountsMatch;

        return new AuditReconciliationResponseDto(
                systemTotal,
                systemBalancesToZero,
                treasuryBalances,
                accountResults,
                overallReconciled
        );
    }
}
