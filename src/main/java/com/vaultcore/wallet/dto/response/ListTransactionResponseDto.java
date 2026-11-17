package com.vaultcore.wallet.dto.response;

import com.vaultcore.wallet.enums.Status;
import com.vaultcore.wallet.enums.TransactionType;
import java.time.Instant;
import java.util.UUID;

public record ListTransactionResponseDto(
        UUID transactionId,
        TransactionType transactionType,
        Long amount,
        String assetCode,
        Status status,
        Instant createdAt
) {
    public ListTransactionResponseDto(
            UUID transactionId,
            TransactionType transactionType,
            Status status,
            Instant createdAt
    ) {
        this(transactionId, transactionType, null, null, status, createdAt);
    }
}
