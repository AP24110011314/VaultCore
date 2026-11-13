package com.service.internalWallet.dto.response;

import java.util.UUID;

public record BalanceResponseDto(
        UUID userId,
        String email,
        String assetCode,
        String assetName,
        Long balance
) {
}
