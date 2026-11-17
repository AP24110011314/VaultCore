package com.vaultcore.wallet.service;

import com.vaultcore.wallet.dto.response.TransactionResponseDto;

public record TransactionResult(
        TransactionResponseDto response,
        boolean replayed
) {
}
