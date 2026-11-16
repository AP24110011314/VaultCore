package com.service.internalWallet.service;

import com.service.internalWallet.dto.request.TransactionRequestDto;
import com.service.internalWallet.dto.response.BalanceResponseDto;
import com.service.internalWallet.dto.response.ListTransactionResponseDto;
import com.service.internalWallet.dto.response.TransactionResponseDto;
import com.service.internalWallet.enums.AccountType;
import com.service.internalWallet.enums.TransactionType;
import com.service.internalWallet.exception.InsufficientFundsException;
import com.service.internalWallet.exception.ResourceNotFoundException;
import com.service.internalWallet.model.Account;
import com.service.internalWallet.model.Asset;
import com.service.internalWallet.model.User;
import com.service.internalWallet.support.AbstractIntegrationTest;
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
        assertThat(history.get(1).transactionType()).isEqualTo(TransactionType.SPEND);
        assertThat(history.get(2).transactionType()).isEqualTo(TransactionType.TOPUP);
    }
}
