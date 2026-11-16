package com.service.internalWallet.config;

import com.service.internalWallet.enums.AccountType;
import com.service.internalWallet.model.Account;
import com.service.internalWallet.model.Asset;
import com.service.internalWallet.model.User;
import com.service.internalWallet.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class DataSeederTest extends AbstractIntegrationTest {

    @Autowired
    private DataSeeder dataSeeder;

    @Test
    void verifiesInitialSeedDataAndIdempotency() {
        // 1. Assets exist
        assertThat(assetRepository.findByCode("GOLD_COINS")).isPresent();
        assertThat(assetRepository.findByCode("DIAMONDS")).isPresent();
        assertThat(assetRepository.findByCode("LOYALTY_POINTS")).isPresent();

        Asset gold = assetRepository.findByCode("GOLD_COINS").orElseThrow();

        // 2. Users and opening balances
        User aditi = userRepository.findByEmail("aditisingh@gmail.com").orElseThrow();
        User priya = userRepository.findByEmail("priyajha@gmail.com").orElseThrow();

        Account aditiGold = accountRepository.findByUserIdAndAssetId(aditi.getId(), gold.getId()).orElseThrow();
        Account priyaGold = accountRepository.findByUserIdAndAssetId(priya.getId(), gold.getId()).orElseThrow();
        Account systemGold = accountRepository.findByTypeAndAssetId(AccountType.SYSTEM, gold.getId()).orElseThrow();

        assertThat(aditiGold.getBalance()).isEqualTo(500L);
        assertThat(priyaGold.getBalance()).isEqualTo(300L);
        assertThat(systemGold.getBalance()).isEqualTo(-800L);

        // 3. Ledger backs opening balances
        assertLedgerMatchesBalance(aditiGold.getId());
        assertLedgerMatchesBalance(priyaGold.getId());
        assertLedgerMatchesBalance(systemGold.getId());
        assertSystemBalancesToZero();

        // 4. Re-running the seeder is idempotent
        dataSeeder.seed();

        Account aditiAfter = accountRepository.findById(aditiGold.getId()).orElseThrow();
        Account priyaAfter = accountRepository.findById(priyaGold.getId()).orElseThrow();
        Account systemAfter = accountRepository.findById(systemGold.getId()).orElseThrow();

        assertThat(aditiAfter.getBalance()).isEqualTo(500L);
        assertThat(priyaAfter.getBalance()).isEqualTo(300L);
        assertThat(systemAfter.getBalance()).isEqualTo(-800L);

        assertLedgerMatchesBalance(aditiAfter.getId());
        assertLedgerMatchesBalance(priyaAfter.getId());
        assertLedgerMatchesBalance(systemAfter.getId());
        assertSystemBalancesToZero();
    }
}
