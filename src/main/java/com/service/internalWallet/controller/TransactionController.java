package com.service.internalWallet.controller;

import com.service.internalWallet.dto.request.TransactionRequestDto;
import com.service.internalWallet.dto.response.BalanceResponseDto;
import com.service.internalWallet.dto.response.ListTransactionResponseDto;
import com.service.internalWallet.dto.response.TransactionResponseDto;
import com.service.internalWallet.service.TransactionResult;
import com.service.internalWallet.service.TransactionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/transactions")
    public ResponseEntity<TransactionResponseDto> createTransaction(@Valid @RequestBody TransactionRequestDto request) {
        TransactionResult result = transactionService.processTransaction(request);
        return ResponseEntity
                .status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(result.response());
    }

    @GetMapping({"/balance", "/wallet/balance"})
    public ResponseEntity<BalanceResponseDto> getBalance(@RequestParam String email, @RequestParam String assetCode) {
        return ResponseEntity.ok(transactionService.getBalance(email, assetCode));
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<ListTransactionResponseDto>> getTransactions(@RequestParam String email) {
        return ResponseEntity.ok(transactionService.getTransactionHistory(email));
    }
}
