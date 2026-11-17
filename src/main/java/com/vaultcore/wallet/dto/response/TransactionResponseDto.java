package com.vaultcore.wallet.dto.response;

import com.vaultcore.wallet.enums.Status;
import com.vaultcore.wallet.enums.TransactionType;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponseDto(
        UUID transactionId,
        TransactionType transactionType,
        Long amount,
        Long balanceAfter,
        Status status,
        Instant createdAt
) {
}
