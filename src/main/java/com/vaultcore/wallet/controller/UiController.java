package com.vaultcore.wallet.controller;

import com.vaultcore.wallet.dto.response.AssetResponseDto;
import com.vaultcore.wallet.dto.response.AuditReconciliationResponseDto;
import com.vaultcore.wallet.dto.response.ListTransactionResponseDto;
import com.vaultcore.wallet.dto.response.UserAssetBalanceResponseDto;
import com.vaultcore.wallet.dto.response.UserResponseDto;
import com.vaultcore.wallet.enums.TransactionType;
import com.vaultcore.wallet.service.TransactionService;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class UiController {

    private final TransactionService transactionService;

    public UiController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    private UserResponseDto resolveUser(List<UserResponseDto> users, String requestedEmail) {
        if (users == null || users.isEmpty()) {
            return null;
        }
        if (requestedEmail != null && !requestedEmail.isBlank()) {
            return users.stream()
                    .filter(u -> u.email().equalsIgnoreCase(requestedEmail.trim()))
                    .findFirst()
                    .orElse(users.get(0));
        }
        return users.get(0);
    }

    @GetMapping("/")
    public String dashboard(@RequestParam(required = false) String email, Model model) {
        List<UserResponseDto> users = transactionService.getAllUsers();
        UserResponseDto selectedUser = resolveUser(users, email);
        String selectedEmail = (selectedUser != null) ? selectedUser.email() : "";

        List<UserAssetBalanceResponseDto> balances = (selectedUser != null)
                ? transactionService.getBalances(selectedEmail)
                : Collections.emptyList();

        List<ListTransactionResponseDto> recentTransactions = (selectedUser != null)
                ? transactionService.getTransactionHistory(selectedEmail, 0, 5)
                : Collections.emptyList();

        model.addAttribute("users", users);
        model.addAttribute("selectedUser", selectedUser);
        model.addAttribute("selectedEmail", selectedEmail);
        model.addAttribute("balances", balances);
        model.addAttribute("recentTransactions", recentTransactions);
        model.addAttribute("activeNav", "dashboard");
        return "dashboard";
    }

    @GetMapping("/transactions/new")
    public String newTransaction(@RequestParam(required = false) String email, Model model) {
        List<UserResponseDto> users = transactionService.getAllUsers();
        UserResponseDto selectedUser = resolveUser(users, email);
        String selectedEmail = (selectedUser != null) ? selectedUser.email() : "";
        List<AssetResponseDto> assets = transactionService.getAllAssets();

        model.addAttribute("users", users);
        model.addAttribute("selectedUser", selectedUser);
        model.addAttribute("selectedEmail", selectedEmail);
        model.addAttribute("assets", assets);
        model.addAttribute("transactionTypes", TransactionType.values());
        model.addAttribute("activeNav", "new-transaction");
        return "transaction-new";
    }

    @GetMapping("/history")
    public String history(
            @RequestParam(required = false) String email,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "10") int size,
            Model model
    ) {
        List<UserResponseDto> users = transactionService.getAllUsers();
        UserResponseDto selectedUser = resolveUser(users, email);
        String selectedEmail = (selectedUser != null) ? selectedUser.email() : "";

        int sanitizedPage = Math.max(0, page);
        int sanitizedSize = Math.min(100, Math.max(1, size));

        List<ListTransactionResponseDto> transactions;
        long totalCount = 0;
        int totalPages = 0;

        if (selectedUser != null) {
            totalCount = transactionService.countTransactions(selectedEmail);
            transactions = transactionService.getTransactionHistory(selectedEmail, sanitizedPage, sanitizedSize);
            totalPages = (int) Math.ceil((double) totalCount / sanitizedSize);
        } else {
            transactions = Collections.emptyList();
        }

        long displayStart = totalCount > 0 ? (long) sanitizedPage * sanitizedSize + 1 : 0;
        long displayEnd = Math.min((long) (sanitizedPage + 1) * sanitizedSize, totalCount);

        model.addAttribute("users", users);
        model.addAttribute("selectedUser", selectedUser);
        model.addAttribute("selectedEmail", selectedEmail);
        model.addAttribute("transactions", transactions);
        model.addAttribute("currentPage", sanitizedPage);
        model.addAttribute("pageSize", sanitizedSize);
        model.addAttribute("totalCount", totalCount);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("displayStart", displayStart);
        model.addAttribute("displayEnd", displayEnd);
        model.addAttribute("hasPrev", sanitizedPage > 0);
        model.addAttribute("hasNext", sanitizedPage + 1 < totalPages);
        model.addAttribute("activeNav", "history");
        return "history";
    }

    @GetMapping("/audit")
    public String audit(Model model) {
        AuditReconciliationResponseDto audit = transactionService.getReconciliationAudit();
        List<UserResponseDto> users = transactionService.getAllUsers();

        model.addAttribute("audit", audit);
        model.addAttribute("users", users);
        model.addAttribute("selectedEmail", !users.isEmpty() ? users.get(0).email() : "");
        model.addAttribute("activeNav", "audit");
        return "audit";
    }
}
