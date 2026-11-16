package com.service.internalWallet.service;

import com.service.internalWallet.dto.request.TransactionRequestDto;
import com.service.internalWallet.dto.response.TransactionResponseDto;
import com.service.internalWallet.enums.AccountType;
import com.service.internalWallet.enums.TransactionType;
import com.service.internalWallet.exception.InsufficientFundsException;
import com.service.internalWallet.model.Account;
import com.service.internalWallet.model.Asset;
import com.service.internalWallet.model.User;
import com.service.internalWallet.support.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private User createTestUser() {
        String email = "concurrent-" + UUID.randomUUID() + "@example.com";
        User user = userRepository.save(new User(null, "Concurrent User", email));

        Asset gold = assetRepository.findByCode("GOLD_COINS").orElseThrow();
        accountRepository.save(new Account(null, user, gold, AccountType.USER, 0L));
        return user;
    }

    @Test
    void noOverdraftUnderConcurrentSpends() throws InterruptedException {
        User user = createTestUser();
        // Give user 100 initial balance
        transactionService.processTransaction(new TransactionRequestDto(
                TransactionType.TOPUP,
                user.getEmail(),
                "GOLD_COINS",
                100L,
                "initial-" + UUID.randomUUID()
        ));

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    transactionService.processTransaction(new TransactionRequestDto(
                            TransactionType.SPEND,
                            user.getEmail(),
                            "GOLD_COINS",
                            10L,
                            "spend-" + UUID.randomUUID()
                    ));
                    successes.incrementAndGet();
                } catch (InsufficientFundsException e) {
                    failures.incrementAndGet();
                } catch (Exception e) {
                    // unexpected
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertThat(doneLatch.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(successes.get()).isEqualTo(10);
        assertThat(failures.get()).isEqualTo(10);

        long balance = transactionService.getBalance(user.getEmail(), "GOLD_COINS").balance();
        assertThat(balance).isEqualTo(0L);

        Asset gold = assetRepository.findByCode("GOLD_COINS").orElseThrow();
        Account account = accountRepository.findByUserIdAndAssetId(user.getId(), gold.getId()).orElseThrow();
        assertLedgerMatchesBalance(account.getId());
        assertSystemBalancesToZero();
    }

    @Test
    void noLostUpdatesUnderConcurrentTopups() throws InterruptedException {
        User user = createTestUser();
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successes = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    transactionService.processTransaction(new TransactionRequestDto(
                            TransactionType.TOPUP,
                            user.getEmail(),
                            "GOLD_COINS",
                            10L,
                            "topup-" + UUID.randomUUID()
                    ));
                    successes.incrementAndGet();
                } catch (Exception e) {
                    // unexpected
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertThat(doneLatch.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(successes.get()).isEqualTo(20);

        long balance = transactionService.getBalance(user.getEmail(), "GOLD_COINS").balance();
        assertThat(balance).isEqualTo(200L);

        Asset gold = assetRepository.findByCode("GOLD_COINS").orElseThrow();
        Account account = accountRepository.findByUserIdAndAssetId(user.getId(), gold.getId()).orElseThrow();
        assertLedgerMatchesBalance(account.getId());
        assertSystemBalancesToZero();
    }

    @Test
    void sharedIdempotencyKeyExecutesExactlyOnce() throws InterruptedException {
        User user = createTestUser();
        String sharedKey = "shared-key-" + UUID.randomUUID();
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        List<TransactionResponseDto> responses = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    TransactionResult result = transactionService.processTransaction(new TransactionRequestDto(
                            TransactionType.TOPUP,
                            user.getEmail(),
                            "GOLD_COINS",
                            50L,
                            sharedKey
                    ));
                    responses.add(result.response());
                } catch (Exception e) {
                    // unexpected
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertThat(doneLatch.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(responses).hasSize(10);
        UUID expectedTxId = responses.get(0).transactionId();
        for (TransactionResponseDto resp : responses) {
            assertThat(resp.transactionId()).isEqualTo(expectedTxId);
            assertThat(resp.amount()).isEqualTo(50L);
            assertThat(resp.balanceAfter()).isEqualTo(50L);
        }

        long balance = transactionService.getBalance(user.getEmail(), "GOLD_COINS").balance();
        assertThat(balance).isEqualTo(50L);

        Asset gold = assetRepository.findByCode("GOLD_COINS").orElseThrow();
        Account account = accountRepository.findByUserIdAndAssetId(user.getId(), gold.getId()).orElseThrow();
        assertLedgerMatchesBalance(account.getId());
        assertSystemBalancesToZero();
    }

    @Test
    void mixedOperationsAcrossMultipleUsersWithoutDeadlock() throws InterruptedException {
        List<User> users = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            User user = createTestUser();
            users.add(user);
            transactionService.processTransaction(new TransactionRequestDto(
                    TransactionType.TOPUP,
                    user.getEmail(),
                    "GOLD_COINS",
                    200L,
                    "seed-user-" + i + "-" + UUID.randomUUID()
            ));
        }

        int operationsCount = 40;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(operationsCount);

        for (int i = 0; i < operationsCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    User user = users.get(index % users.size());
                    TransactionType type = (index % 3 == 0) ? TransactionType.SPEND :
                            (index % 3 == 1) ? TransactionType.TOPUP : TransactionType.BONUS;
                    long amount = 10L;

                    transactionService.processTransaction(new TransactionRequestDto(
                            type,
                            user.getEmail(),
                            "GOLD_COINS",
                            amount,
                            "mixed-" + index + "-" + UUID.randomUUID()
                    ));
                } catch (InsufficientFundsException e) {
                    // expected if balance drops low
                } catch (Exception e) {
                    // unexpected
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertThat(doneLatch.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        Asset gold = assetRepository.findByCode("GOLD_COINS").orElseThrow();
        for (User user : users) {
            Account account = accountRepository.findByUserIdAndAssetId(user.getId(), gold.getId()).orElseThrow();
            assertLedgerMatchesBalance(account.getId());
        }
        assertSystemBalancesToZero();
    }
}
