package com.service.internalWallet.model;

import com.service.internalWallet.enums.AccountType;
import com.service.internalWallet.exception.InsufficientFundsException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    @Test
    void creditIncreasesBalance() {
        Account account = new Account();
        account.setType(AccountType.USER);
        account.setBalance(100L);

        account.credit(50L);

        assertThat(account.getBalance()).isEqualTo(150L);
    }

    @Test
    void debitDecreasesBalance() {
        Account account = new Account();
        account.setType(AccountType.USER);
        account.setBalance(100L);

        account.debit(40L);

        assertThat(account.getBalance()).isEqualTo(60L);
    }

    @Test
    void userAccountCannotBeOverdrawn() {
        Account account = new Account();
        account.setType(AccountType.USER);
        account.setBalance(50L);

        assertThatThrownBy(() -> account.debit(60L))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessage("Insufficient balance");
    }

    @Test
    void systemAccountCanGoNegative() {
        Account account = new Account();
        account.setType(AccountType.SYSTEM);
        account.setBalance(0L);

        account.debit(100L);

        assertThat(account.getBalance()).isEqualTo(-100L);
    }

    @Test
    void invalidAmountsThrowException() {
        Account account = new Account();
        account.setType(AccountType.USER);

        assertThatThrownBy(() -> account.credit(0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.credit(-10L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.debit(0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.debit(-10L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void overflowDetectedOnCredit() {
        Account account = new Account();
        account.setType(AccountType.USER);
        account.setBalance(Long.MAX_VALUE - 10);

        assertThatThrownBy(() -> account.credit(20L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Amount would overflow the balance");
    }

    @Test
    void overflowDetectedOnDebit() {
        Account account = new Account();
        account.setType(AccountType.SYSTEM);
        account.setBalance(Long.MIN_VALUE + 10);

        assertThatThrownBy(() -> account.debit(20L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Amount would overflow the balance");
    }
}
