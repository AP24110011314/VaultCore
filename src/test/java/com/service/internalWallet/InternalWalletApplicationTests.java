package com.service.internalWallet;

import com.service.internalWallet.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

class InternalWalletApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
        assertSystemBalancesToZero();
    }
}
