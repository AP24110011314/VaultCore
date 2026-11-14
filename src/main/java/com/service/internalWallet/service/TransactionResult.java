package com.service.internalWallet.service;

import com.service.internalWallet.dto.response.TransactionResponseDto;

public record TransactionResult(
        TransactionResponseDto response,
        boolean replayed
) {
}
