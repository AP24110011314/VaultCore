package com.vaultcore.wallet;

import com.vaultcore.wallet.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

class VaultCoreApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
        assertSystemBalancesToZero();
    }
}
