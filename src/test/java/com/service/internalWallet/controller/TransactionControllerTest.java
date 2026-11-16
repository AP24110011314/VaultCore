package com.service.internalWallet.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.service.internalWallet.dto.request.TransactionRequestDto;
import com.service.internalWallet.enums.AccountType;
import com.service.internalWallet.enums.TransactionType;
import com.service.internalWallet.exception.GlobalExceptionHandler;
import com.service.internalWallet.model.Account;
import com.service.internalWallet.model.Asset;
import com.service.internalWallet.model.User;
import com.service.internalWallet.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
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
}
