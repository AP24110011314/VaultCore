package com.vaultcore.wallet.support;

import com.vaultcore.wallet.model.Account;
import com.vaultcore.wallet.repository.AccountRepository;
import com.vaultcore.wallet.repository.AssetRepository;
import com.vaultcore.wallet.repository.LedgerEntryRepository;
import com.vaultcore.wallet.repository.TransactionRepository;
import com.vaultcore.wallet.repository.UserRepository;
import com.vaultcore.wallet.service.TransactionService;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @Autowired
    protected AccountRepository accountRepository;

    @Autowired
    protected LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected AssetRepository assetRepository;

    @Autowired
    protected TransactionRepository transactionRepository;

    @Autowired
    protected TransactionService transactionService;

    protected void assertLedgerMatchesBalance(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AssertionError("Account not found: " + accountId));
        Long expectedLedgerSum = ledgerEntryRepository.sumAmountsByAccountId(accountId);
        long expected = expectedLedgerSum != null ? expectedLedgerSum : 0L;
        assertThat(account.getBalance())
                .as("Cached balance for account %s should equal ledger sum", accountId)
                .isEqualTo(expected);
    }

    protected void assertSystemBalancesToZero() {
        long totalBalance = accountRepository.sumAllBalances();
        assertThat(totalBalance)
                .as("Total balance across all accounts in the system must be zero")
                .isEqualTo(0L);
    }
}
