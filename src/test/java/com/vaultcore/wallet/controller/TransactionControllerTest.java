package com.vaultcore.wallet.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vaultcore.wallet.dto.request.TransactionRequestDto;
import com.vaultcore.wallet.enums.AccountType;
import com.vaultcore.wallet.enums.TransactionType;
import com.vaultcore.wallet.exception.GlobalExceptionHandler;
import com.vaultcore.wallet.model.Account;
import com.vaultcore.wallet.model.Asset;
import com.vaultcore.wallet.model.User;
import com.vaultcore.wallet.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransactionControllerTest extends AbstractIntegrationTest {

    @Autowired
    private TransactionController transactionController;

    @Autowired
    private GlobalExceptionHandler globalExceptionHandler;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(transactionController)
                .setControllerAdvice(globalExceptionHandler)
                .build();
    }

    private User createTestUser() {
        String email = "controller-" + UUID.randomUUID() + "@example.com";
        User user = userRepository.save(new User(null, "Controller User", email));

        Asset gold = assetRepository.findByCode("GOLD_COINS").orElseThrow();
        accountRepository.save(new Account(null, user, gold, AccountType.USER, 0L));
        return user;
    }

    @Test
    void createTransactionReturns201Then200OnReplay() throws Exception {
        User user = createTestUser();
        String key = "key-" + UUID.randomUUID();

        TransactionRequestDto dto = new TransactionRequestDto(
                TransactionType.TOPUP,
                user.getEmail(),
                "GOLD_COINS",
                100L,
                key
        );

        // First call: 201 Created
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount", is(100)))
                .andExpect(jsonPath("$.balanceAfter", is(100)))
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.transactionType", is("TOPUP")));

        // Replay call: 200 OK
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount", is(100)))
                .andExpect(jsonPath("$.balanceAfter", is(100)))
                .andExpect(jsonPath("$.status", is("SUCCESS")));
    }

    @Test
    void validationErrorReturns400WithSpecificBody() throws Exception {
        TransactionRequestDto badDto = new TransactionRequestDto(
                TransactionType.TOPUP,
                "invalid-email",
                "",
                -5L,
                ""
        );

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Validation Failed")))
                .andExpect(jsonPath("$.message", containsString("Amount must be greater than 0")))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"INVALID_TYPE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Invalid Data")))
                .andExpect(jsonPath("$.message", is("Malformed request body (check JSON syntax and enum values such as type)")));
    }

    @Test
    void insufficientFundsReturns400() throws Exception {
        User user = createTestUser();
        TransactionRequestDto dto = new TransactionRequestDto(
                TransactionType.SPEND,
                user.getEmail(),
                "GOLD_COINS",
                50L,
                "spend-fail-" + UUID.randomUUID()
        );

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Invalid Data")))
                .andExpect(jsonPath("$.message", is("Insufficient balance")));
    }

    @Test
    void notFoundReturns404() throws Exception {
        TransactionRequestDto dto = new TransactionRequestDto(
                TransactionType.TOPUP,
                "unknown@example.com",
                "GOLD_COINS",
                50L,
                "nf-" + UUID.randomUUID()
        );

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", is("User not found")));
    }

    @Test
    void bothBalancePathsWork() throws Exception {
        User user = createTestUser();

        mockMvc.perform(get("/api/v1/balance")
                        .param("email", user.getEmail())
                        .param("assetCode", "GOLD_COINS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is(user.getEmail())))
                .andExpect(jsonPath("$.assetCode", is("GOLD_COINS")))
                .andExpect(jsonPath("$.balance", is(0)));

        mockMvc.perform(get("/api/v1/wallet/balance")
                        .param("email", user.getEmail())
                        .param("assetCode", "GOLD_COINS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is(user.getEmail())))
                .andExpect(jsonPath("$.balance", is(0)));
    }

    @Test
    void missingBalanceParamReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/balance")
                        .param("email", "any@example.com"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Invalid Data")))
                .andExpect(jsonPath("$.message", is("Required parameter 'assetCode' is missing")));
    }

    @Test
    void getTransactionsHistoryReturnsList() throws Exception {
        User user = createTestUser();

        mockMvc.perform(get("/api/v1/transactions")
                        .param("email", user.getEmail()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getAssetsReturnsAllConfiguredAssets() throws Exception {
        mockMvc.perform(get("/api/v1/assets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(3))))
                .andExpect(jsonPath("$[?(@.code == 'GOLD_COINS')].name", notNullValue()))
                .andExpect(jsonPath("$[?(@.code == 'DIAMONDS')].name", notNullValue()))
                .andExpect(jsonPath("$[?(@.code == 'LOYALTY_POINTS')].name", notNullValue()));
    }

    @Test
    void getUsersReturnsSeedUsers() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[?(@.email == 'aditisingh@gmail.com')].name", notNullValue()))
                .andExpect(jsonPath("$[?(@.email == 'priyajha@gmail.com')].name", notNullValue()));
    }

    @Test
    void getBalancesReturnsAllBalancesForUser() throws Exception {
        User user = createTestUser();

        mockMvc.perform(get("/api/v1/balances")
                        .param("email", user.getEmail()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(3))))
                .andExpect(jsonPath("$[?(@.assetCode == 'GOLD_COINS')].balance", is(java.util.List.of(0))));
    }

    @Test
    void getBalancesForUnknownUserReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/balances")
                        .param("email", "unknown-" + UUID.randomUUID() + "@example.com"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", is("User not found")));
    }

    @Test
    void getTransactionsHistoryPopulatesAmountAndAssetCodeAndReturnsPlainArray() throws Exception {
        User user = createTestUser();
        transactionService.processTransaction(new TransactionRequestDto(
                TransactionType.TOPUP,
                user.getEmail(),
                "GOLD_COINS",
                250L,
                "history-key-" + UUID.randomUUID()
        ));

        mockMvc.perform(get("/api/v1/transactions")
                        .param("email", user.getEmail()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].transactionType", is("TOPUP")))
                .andExpect(jsonPath("$[0].amount", is(250)))
                .andExpect(jsonPath("$[0].assetCode", is("GOLD_COINS")))
                .andExpect(jsonPath("$[0].status", is("SUCCESS")));
    }

    @Test
    void getTransactionsWithPaginationReturnsPlainArray() throws Exception {
        User user = createTestUser();
        for (int i = 0; i < 5; i++) {
            transactionService.processTransaction(new TransactionRequestDto(
                    TransactionType.TOPUP,
                    user.getEmail(),
                    "GOLD_COINS",
                    (long) (10 + i),
                    "page-key-" + i + "-" + UUID.randomUUID()
            ));
        }

        mockMvc.perform(get("/api/v1/transactions")
                        .param("email", user.getEmail())
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].amount", notNullValue()))
                .andExpect(jsonPath("$[0].assetCode", is("GOLD_COINS")));
    }

    @Test
    void getAuditReconciliationReturnsBalancedSystem() throws Exception {
        mockMvc.perform(get("/api/v1/audit/reconciliation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.systemTotalBalance", is(0)))
                .andExpect(jsonPath("$.systemBalancesToZero", is(true)))
                .andExpect(jsonPath("$.allReconciled", is(true)))
                .andExpect(jsonPath("$.treasuryBalances", hasSize(3)))
                .andExpect(jsonPath("$.accounts", hasSize(greaterThanOrEqualTo(5))));
    }
}
