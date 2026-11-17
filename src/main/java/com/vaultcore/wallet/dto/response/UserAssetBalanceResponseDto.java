package com.vaultcore.wallet.dto.response;

public record UserAssetBalanceResponseDto(
        String assetCode,
        String assetName,
        long balance
) {}
