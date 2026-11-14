package com.service.internalWallet.service;

import com.service.internalWallet.dto.request.TransactionRequestDto;
import com.service.internalWallet.dto.response.BalanceResponseDto;
import com.service.internalWallet.dto.response.ListTransactionResponseDto;
import com.service.internalWallet.exception.ResourceNotFoundException;
import com.service.internalWallet.model.Account;
import com.service.internalWallet.model.Asset;
import com.service.internalWallet.model.Transaction;
import com.service.internalWallet.model.User;
import com.service.internalWallet.repository.AccountRepository;
import com.service.internalWallet.repository.AssetRepository;
import com.service.internalWallet.repository.TransactionRepository;
import com.service.internalWallet.repository.UserRepository;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private final TransactionProcessor transactionProcessor;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final AccountRepository accountRepository;

    public TransactionService(TransactionProcessor transactionProcessor,
                              TransactionRepository transactionRepository,
                              UserRepository userRepository,
                              AssetRepository assetRepository,
                              AccountRepository accountRepository) {
        this.transactionProcessor = transactionProcessor;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.assetRepository = assetRepository;
        this.accountRepository = accountRepository;
    }

    public TransactionResult processTransaction(TransactionRequestDto request) {
        try {
            return transactionProcessor.process(request);
        } catch (DataIntegrityViolationException ex) {
            return replayAfterRace(request.idempotencyKey(), ex);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public TransactionResult replayAfterRace(String idempotencyKey, DataIntegrityViolationException originalException) {
        return transactionRepository.findByIdempotencyKey(idempotencyKey)
                .map(transactionProcessor::replay)
                .orElseThrow(() -> originalException);
    }

    @Transactional(readOnly = true)
    public BalanceResponseDto getBalance(String email, String assetCode) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Asset asset = assetRepository.findByCode(assetCode)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found"));

        Account account = accountRepository.findByUserIdAndAssetId(user.getId(), asset.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        return new BalanceResponseDto(
                user.getId(),
                user.getEmail(),
                asset.getCode(),
                asset.getName(),
                account.getBalance()
        );
    }

    @Transactional(readOnly = true)
    public List<ListTransactionResponseDto> getTransactionHistory(String email) {
        List<Transaction> transactions = transactionRepository.findByUserEmail(email);
        return transactions.stream()
                .map(t -> new ListTransactionResponseDto(
                        t.getId(),
                        t.getType(),
                        t.getStatus(),
                        t.getCreatedAt()
                ))
                .toList();
    }
}
