package com.vaultcore.wallet.dto.response;

public record TreasuryBalanceDto(
        String assetCode,
        String assetName,
        long balance
) {}
