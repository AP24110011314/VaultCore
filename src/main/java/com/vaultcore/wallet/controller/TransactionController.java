package com.vaultcore.wallet.controller;

import com.vaultcore.wallet.dto.request.TransactionRequestDto;
import com.vaultcore.wallet.dto.response.AssetResponseDto;
import com.vaultcore.wallet.dto.response.AuditReconciliationResponseDto;
import com.vaultcore.wallet.dto.response.BalanceResponseDto;
import com.vaultcore.wallet.dto.response.ListTransactionResponseDto;
import com.vaultcore.wallet.dto.response.TransactionResponseDto;
import com.vaultcore.wallet.dto.response.UserAssetBalanceResponseDto;
import com.vaultcore.wallet.dto.response.UserResponseDto;
import com.vaultcore.wallet.service.TransactionResult;
import com.vaultcore.wallet.service.TransactionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
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

    @GetMapping("/balances")
    public ResponseEntity<List<UserAssetBalanceResponseDto>> getBalances(@RequestParam String email) {
        return ResponseEntity.ok(transactionService.getBalances(email));
    }

    @GetMapping("/assets")
    public ResponseEntity<List<AssetResponseDto>> getAssets() {
        return ResponseEntity.ok(transactionService.getAllAssets());
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserResponseDto>> getUsers() {
        return ResponseEntity.ok(transactionService.getAllUsers());
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<ListTransactionResponseDto>> getTransactions(
            @RequestParam String email,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        if (page != null || size != null) {
            int pageNum = (page != null) ? Math.max(0, page) : 0;
            int pageSize = (size != null) ? Math.min(100, Math.max(1, size)) : 50;
            long total = transactionService.countTransactions(email);
            List<ListTransactionResponseDto> results = transactionService.getTransactionHistory(email, pageNum, pageSize);
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Total-Count", String.valueOf(total));
            headers.add("X-Page-Number", String.valueOf(pageNum));
            headers.add("X-Page-Size", String.valueOf(pageSize));
            return ResponseEntity.ok().headers(headers).body(results);
        }

        return ResponseEntity.ok(transactionService.getTransactionHistory(email));
    }

    @GetMapping("/audit/reconciliation")
    public ResponseEntity<AuditReconciliationResponseDto> getAuditReconciliation() {
        return ResponseEntity.ok(transactionService.getReconciliationAudit());
    }
}
