package com.service.internalWallet.dto.response;

import com.service.internalWallet.enums.Status;
import com.service.internalWallet.enums.TransactionType;
import java.time.Instant;
import java.util.UUID;

public record ListTransactionResponseDto(
        UUID transactionId,
        TransactionType transactionType,
        Status status,
        Instant createdAt
) {
}
