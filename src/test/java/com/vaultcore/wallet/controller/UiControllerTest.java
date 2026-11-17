package com.vaultcore.wallet.controller;

import com.vaultcore.wallet.dto.request.TransactionRequestDto;
import com.vaultcore.wallet.enums.AccountType;
import com.vaultcore.wallet.enums.TransactionType;
import com.vaultcore.wallet.model.Account;
import com.vaultcore.wallet.model.Asset;
import com.vaultcore.wallet.model.User;
import com.vaultcore.wallet.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class UiControllerTest extends AbstractIntegrationTest {

    @Autowired
    private org.springframework.web.context.WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    private User createFreshTestUser() {
        String email = "ui-test-" + UUID.randomUUID() + "@example.com";
        User user = userRepository.save(new User(null, "UI Test User", email));
        Asset gold = assetRepository.findByCode("GOLD_COINS").orElseThrow();
        Asset diamonds = assetRepository.findByCode("DIAMONDS").orElseThrow();
        accountRepository.save(new Account(null, user, gold, AccountType.USER, 0L));
        accountRepository.save(new Account(null, user, diamonds, AccountType.USER, 0L));
        return user;
    }

    @Test
    void dashboardPageRendersSuccessfully() throws Exception {
        User user = createFreshTestUser();
        transactionService.processTransaction(new TransactionRequestDto(
                TransactionType.TOPUP,
                user.getEmail(),
                "GOLD_COINS",
                120L,
                "ui-key-" + UUID.randomUUID()
        ));

        mockMvc.perform(get("/").param("email", user.getEmail()))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attributeExists("users"))
                .andExpect(model().attributeExists("selectedUser"))
                .andExpect(model().attributeExists("balances"))
                .andExpect(model().attributeExists("recentTransactions"))
                .andExpect(model().attribute("activeNav", "dashboard"))
                .andExpect(content().string(containsString("VaultCore")))
                .andExpect(content().string(containsString("Wallet Overview")))
                .andExpect(content().string(containsString("<meta name=\"color-scheme\" content=\"light\">")))
                .andExpect(content().string(not(containsString("themeToggleBtn"))));
    }

    @Test
    void newTransactionPageRendersSuccessfully() throws Exception {
        User user = createFreshTestUser();

        mockMvc.perform(get("/transactions/new").param("email", user.getEmail()))
                .andExpect(status().isOk())
                .andExpect(view().name("transaction-new"))
                .andExpect(model().attributeExists("users"))
                .andExpect(model().attributeExists("assets"))
                .andExpect(model().attributeExists("transactionTypes"))
                .andExpect(model().attribute("activeNav", "new-transaction"))
                .andExpect(content().string(containsString("VaultCore")))
                .andExpect(content().string(containsString("Execute Transaction")))
                .andExpect(content().string(containsString("<meta name=\"color-scheme\" content=\"light\">")))
                .andExpect(content().string(not(containsString("themeToggleBtn"))))
                .andExpect(content().string(containsString("segmented-control")));
    }

    @Test
    void historyPageRendersSuccessfullyWithPagination() throws Exception {
        User user = createFreshTestUser();
        for (int i = 0; i < 3; i++) {
            transactionService.processTransaction(new TransactionRequestDto(
                    TransactionType.TOPUP,
                    user.getEmail(),
                    "GOLD_COINS",
                    (long) (50 + i),
                    "hist-ui-" + i + "-" + UUID.randomUUID()
            ));
        }

        mockMvc.perform(get("/history")
                        .param("email", user.getEmail())
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(view().name("history"))
                .andExpect(model().attributeExists("transactions"))
                .andExpect(model().attribute("currentPage", 0))
                .andExpect(model().attribute("pageSize", 2))
                .andExpect(model().attribute("totalCount", 3L))
                .andExpect(model().attribute("totalPages", 2))
                .andExpect(model().attribute("hasPrev", false))
                .andExpect(model().attribute("hasNext", true))
                .andExpect(model().attribute("activeNav", "history"))
                .andExpect(content().string(containsString("VaultCore")))
                .andExpect(content().string(containsString("Transaction History")))
                .andExpect(content().string(containsString("<meta name=\"color-scheme\" content=\"light\">")))
                .andExpect(content().string(not(containsString("themeToggleBtn"))));
    }

    @Test
    void auditPageRendersSuccessfully() throws Exception {
        mockMvc.perform(get("/audit"))
                .andExpect(status().isOk())
                .andExpect(view().name("audit"))
                .andExpect(model().attributeExists("audit"))
                .andExpect(model().attribute("activeNav", "audit"))
                .andExpect(content().string(containsString("VaultCore")))
                .andExpect(content().string(containsString("Ledger Integrity")))
                .andExpect(content().string(containsString("<meta name=\"color-scheme\" content=\"light\">")))
                .andExpect(content().string(not(containsString("themeToggleBtn"))));
    }
}
