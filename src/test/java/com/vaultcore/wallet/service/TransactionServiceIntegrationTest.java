package com.vaultcore.wallet.service;

import com.vaultcore.wallet.dto.request.TransactionRequestDto;
import com.vaultcore.wallet.dto.response.AssetResponseDto;
import com.vaultcore.wallet.dto.response.AuditReconciliationResponseDto;
import com.vaultcore.wallet.dto.response.BalanceResponseDto;
import com.vaultcore.wallet.dto.response.ListTransactionResponseDto;
import com.vaultcore.wallet.dto.response.TransactionResponseDto;
import com.vaultcore.wallet.dto.response.UserAssetBalanceResponseDto;
import com.vaultcore.wallet.dto.response.UserResponseDto;
import com.vaultcore.wallet.enums.AccountType;
import com.vaultcore.wallet.enums.TransactionType;
import com.vaultcore.wallet.exception.InsufficientFundsException;
import com.vaultcore.wallet.exception.ResourceNotFoundException;
import com.vaultcore.wallet.model.Account;
import com.vaultcore.wallet.model.Asset;
import com.vaultcore.wallet.model.User;
import com.vaultcore.wallet.support.AbstractIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionServiceIntegrationTest extends AbstractIntegrationTest {

    private User createTestUser() {
        String email = "test-" + UUID.randomUUID() + "@example.com";
        User user = userRepository.save(new User(null, "Test User", email));

        Asset gold = assetRepository.findByCode("GOLD_COINS").orElseThrow();
        Asset diamonds = assetRepository.findByCode("DIAMONDS").orElseThrow();

        accountRepository.save(new Account(null, user, gold, AccountType.USER, 0L));
        accountRepository.save(new Account(null, user, diamonds, AccountType.USER, 0L));
        return user;
    }

    @Test
    void topUpIncreasesBalanceAndMaintainsInvariants() {
        User user = createTestUser();
        String key = "topup-" + UUID.randomUUID();

        TransactionResult result = transactionService.processTransaction(new TransactionRequestDto(
                TransactionType.TOPUP,
                user.getEmail(),
                "GOLD_COINS",
                100L,
                key
        ));

        assertThat(result.replayed()).isFalse();
        TransactionResponseDto response = result.response();
        assertThat(response.amount()).isEqualTo(100L);
        assertThat(response.balanceAfter()).isEqualTo(100L);

        BalanceResponseDto balance = transactionService.getBalance(user.getEmail(), "GOLD_COINS");
        assertThat(balance.balance()).isEqualTo(100L);

        Asset gold = assetRepository.findByCode("GOLD_COINS").orElseThrow();
        Account account = accountRepository.findByUserIdAndAssetId(user.getId(), gold.getId()).orElseThrow();
        assertLedgerMatchesBalance(account.getId());
        assertSystemBalancesToZero();
    }

    @Test
    void bonusIncreasesBalance() {
        User user = createTestUser();
        String key = "bonus-" + UUID.randomUUID();

        TransactionResult result = transactionService.processTransaction(new TransactionRequestDto(
                TransactionType.BONUS,
                user.getEmail(),
                "GOLD_COINS",
                50L,
                key
        ));

        assertThat(result.replayed()).isFalse();
        assertThat(result.response().balanceAfter()).isEqualTo(50L);

        BalanceResponseDto balance = transactionService.getBalance(user.getEmail(), "GOLD_COINS");
        assertThat(balance.balance()).isEqualTo(50L);

        assertSystemBalancesToZero();
    }

    @Test
    void spendDecreasesBalance() {
        User user = createTestUser();
        transactionService.processTransaction(new TransactionRequestDto(
                TransactionType.TOPUP,
                user.getEmail(),
                "GOLD_COINS",
                100L,
                "topup-" + UUID.randomUUID()
        ));

        TransactionResult spendResult = transactionService.processTransaction(new TransactionRequestDto(
                TransactionType.SPEND,
                user.getEmail(),
                "GOLD_COINS",
                40L,
                "spend-" + UUID.randomUUID()
        ));

        assertThat(spendResult.replayed()).isFalse();
        assertThat(spendResult.response().balanceAfter()).isEqualTo(60L);

        BalanceResponseDto balance = transactionService.getBalance(user.getEmail(), "GOLD_COINS");
        assertThat(balance.balance()).isEqualTo(60L);

        Asset gold = assetRepository.findByCode("GOLD_COINS").orElseThrow();
        Account account = accountRepository.findByUserIdAndAssetId(user.getId(), gold.getId()).orElseThrow();
        assertLedgerMatchesBalance(account.getId());
        assertSystemBalancesToZero();
    }

    @Test
    void exactBalanceSpendWorks() {
        User user = createTestUser();
        transactionService.processTransaction(new TransactionRequestDto(
                TransactionType.TOPUP,
                user.getEmail(),
                "GOLD_COINS",
                100L,
                "topup-" + UUID.randomUUID()
        ));

        TransactionResult spendResult = transactionService.processTransaction(new TransactionRequestDto(
                TransactionType.SPEND,
                user.getEmail(),
                "GOLD_COINS",
                100L,
                "spend-" + UUID.randomUUID()
        ));

        assertThat(spendResult.response().balanceAfter()).isEqualTo(0L);
        assertThat(transactionService.getBalance(user.getEmail(), "GOLD_COINS").balance()).isEqualTo(0L);
        assertSystemBalancesToZero();
    }

    @Test
    void insufficientFundsLeavesNoTrace() {
        User user = createTestUser();
        transactionService.processTransaction(new TransactionRequestDto(
                TransactionType.TOPUP,
                user.getEmail(),
                "GOLD_COINS",
                50L,
                "topup-" + UUID.randomUUID()
        ));

        String failedKey = "failed-spend-" + UUID.randomUUID();
        assertThatThrownBy(() -> transactionService.processTransaction(new TransactionRequestDto(
                TransactionType.SPEND,
                user.getEmail(),
                "GOLD_COINS",
                60L,
                failedKey
        ))).isInstanceOf(InsufficientFundsException.class)
                .hasMessage("Insufficient balance");

        assertThat(transactionService.getBalance(user.getEmail(), "GOLD_COINS").balance()).isEqualTo(50L);
        assertThat(transactionRepository.findByIdempotencyKey(failedKey)).isEmpty();
        assertSystemBalancesToZero();
    }

    @Test
    void replayReturnsOriginalResultEvenIfBalanceChangedLater() {
        User user = createTestUser();
        String key1 = "replay-k1-" + UUID.randomUUID();

        TransactionResult firstResult = transactionService.processTransaction(new TransactionRequestDto(
                TransactionType.TOPUP,
                user.getEmail(),
                "GOLD_COINS",
                100L,
                key1
        ));

        // Subsequent spend changes balance
        transactionService.processTransaction(new TransactionRequestDto(
                TransactionType.SPEND,
                user.getEmail(),
                "GOLD_COINS",
                30L,
                "spend-" + UUID.randomUUID()
        ));

        assertThat(transactionService.getBalance(user.getEmail(), "GOLD_COINS").balance()).isEqualTo(70L);

        // Replay first request
        TransactionResult replayResult = transactionService.processTransaction(new TransactionRequestDto(
                TransactionType.TOPUP,
                user.getEmail(),
                "GOLD_COINS",
                100L,
                key1
        ));

        assertThat(replayResult.replayed()).isTrue();
        assertThat(replayResult.response().transactionId()).isEqualTo(firstResult.response().transactionId());
        assertThat(replayResult.response().amount()).isEqualTo(100L);
        assertThat(replayResult.response().balanceAfter()).isEqualTo(100L); // historical value!
        assertThat(transactionService.getBalance(user.getEmail(), "GOLD_COINS").balance()).isEqualTo(70L);
    }

    @Test
    void notFoundScenarios() {
        String key = "nf-" + UUID.randomUUID();

        assertThatThrownBy(() -> transactionService.processTransaction(new TransactionRequestDto(
                TransactionType.TOPUP,
                "nonexistent@example.com",
                "GOLD_COINS",
                100L,
                key
        ))).isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");

        User user = createTestUser();
        assertThatThrownBy(() -> transactionService.processTransaction(new TransactionRequestDto(
                TransactionType.TOPUP,
                user.getEmail(),
                "NON_EXISTENT_ASSET",
                100L,
                key
        ))).isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Asset not found");
    }

    @Test
    void perAssetBalancesAreIsolated() {
        User user = createTestUser();

        transactionService.processTransaction(new TransactionRequestDto(
                TransactionType.TOPUP,
                user.getEmail(),
                "GOLD_COINS",
                100L,
                "topup-gold-" + UUID.randomUUID()
        ));

        transactionService.processTransaction(new TransactionRequestDto(
                TransactionType.TOPUP,
                user.getEmail(),
                "DIAMONDS",
                50L,
                "topup-diamonds-" + UUID.randomUUID()
        ));

        assertThat(transactionService.getBalance(user.getEmail(), "GOLD_COINS").balance()).isEqualTo(100L);
        assertThat(transactionService.getBalance(user.getEmail(), "DIAMONDS").balance()).isEqualTo(50L);
    }

    @Test
    void historyOrderingAndScoping() throws InterruptedException {
        User userA = createTestUser();
        User userB = createTestUser();

        transactionService.processTransaction(new TransactionRequestDto(
                TransactionType.TOPUP,
                userA.getEmail(),
                "GOLD_COINS",
                10L,
                "tx1-" + UUID.randomUUID()
        ));

        Thread.sleep(15);

        transactionService.processTransaction(new TransactionRequestDto(
                TransactionType.SPEND,
                userA.getEmail(),
                "GOLD_COINS",
                5L,
                "tx2-" + UUID.randomUUID()
        ));

        Thread.sleep(15);

        transactionService.processTransaction(new TransactionRequestDto(
                TransactionType.BONUS,
                userA.getEmail(),
                "GOLD_COINS",
                20L,
                "tx3-" + UUID.randomUUID()
        ));

        transactionService.processTransaction(new TransactionRequestDto(
                TransactionType.TOPUP,
                userB.getEmail(),
                "GOLD_COINS",
                100L,
                "tx-b-" + UUID.randomUUID()
        ));

        List<ListTransactionResponseDto> history = transactionService.getTransactionHistory(userA.getEmail());
        assertThat(history).hasSize(3);
        assertThat(history.get(0).transactionType()).isEqualTo(TransactionType.BONUS);
        assertThat(history.get(0).amount()).isEqualTo(20L);
        assertThat(history.get(0).assetCode()).isEqualTo("GOLD_COINS");
        assertThat(history.get(1).transactionType()).isEqualTo(TransactionType.SPEND);
        assertThat(history.get(1).amount()).isEqualTo(5L);
        assertThat(history.get(1).assetCode()).isEqualTo("GOLD_COINS");
        assertThat(history.get(2).transactionType()).isEqualTo(TransactionType.TOPUP);
        assertThat(history.get(2).amount()).isEqualTo(10L);
        assertThat(history.get(2).assetCode()).isEqualTo("GOLD_COINS");
    }

    @Test
    void getAllAssetsReturnsListOfAssets() {
        List<AssetResponseDto> assets = transactionService.getAllAssets();
        assertThat(assets).hasSizeGreaterThanOrEqualTo(3);
        assertThat(assets).extracting(AssetResponseDto::code)
                .contains("GOLD_COINS", "DIAMONDS", "LOYALTY_POINTS");
    }

    @Test
    void getAllUsersReturnsUsers() {
        User user = createTestUser();
        List<UserResponseDto> users = transactionService.getAllUsers();
        assertThat(users).extracting(UserResponseDto::email)
                .contains(user.getEmail(), "aditisingh@gmail.com", "priyajha@gmail.com");
    }

    @Test
    void getBalancesReturnsAllAssetsForUser() {
        User user = createTestUser();
        transactionService.processTransaction(new TransactionRequestDto(
                TransactionType.TOPUP,
                user.getEmail(),
                "GOLD_COINS",
                150L,
                "balances-" + UUID.randomUUID()
        ));

        List<UserAssetBalanceResponseDto> balances = transactionService.getBalances(user.getEmail());
        assertThat(balances).hasSizeGreaterThanOrEqualTo(3);

        UserAssetBalanceResponseDto gold = balances.stream()
                .filter(b -> "GOLD_COINS".equals(b.assetCode()))
                .findFirst()
                .orElseThrow();
        assertThat(gold.balance()).isEqualTo(150L);

        UserAssetBalanceResponseDto diamonds = balances.stream()
                .filter(b -> "DIAMONDS".equals(b.assetCode()))
                .findFirst()
                .orElseThrow();
        assertThat(diamonds.balance()).isEqualTo(0L);
    }

    @Test
    void getBalancesThrowsOnUnknownUser() {
        assertThatThrownBy(() -> transactionService.getBalances("unknown@nowhere.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");
    }

    @Test
    void getTransactionHistoryPagination() {
        User user = createTestUser();
        for (int i = 0; i < 5; i++) {
            transactionService.processTransaction(new TransactionRequestDto(
                    TransactionType.TOPUP,
                    user.getEmail(),
                    "GOLD_COINS",
                    (long) (i + 1),
                    "page-key-" + i + "-" + UUID.randomUUID()
            ));
        }

        List<ListTransactionResponseDto> page1 = transactionService.getTransactionHistory(user.getEmail(), 0, 2);
        assertThat(page1).hasSize(2);
        assertThat(page1.get(0).amount()).isEqualTo(5L);
        assertThat(page1.get(1).amount()).isEqualTo(4L);

        List<ListTransactionResponseDto> page2 = transactionService.getTransactionHistory(user.getEmail(), 1, 2);
        assertThat(page2).hasSize(2);
        assertThat(page2.get(0).amount()).isEqualTo(3L);
        assertThat(page2.get(1).amount()).isEqualTo(2L);
    }

    @Test
    void getReconciliationAuditReturnsZeroSumAndMatchesLedger() {
        AuditReconciliationResponseDto audit = transactionService.getReconciliationAudit();
        assertThat(audit.systemTotalBalance()).isEqualTo(0L);
        assertThat(audit.systemBalancesToZero()).isTrue();
        assertThat(audit.allReconciled()).isTrue();
        assertThat(audit.treasuryBalances()).hasSize(3);
        assertThat(audit.accounts()).isNotEmpty();
    }
}
