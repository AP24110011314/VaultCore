package com.vaultcore.wallet.dto.response;

import java.util.List;

public record AuditReconciliationResponseDto(
        long systemTotalBalance,
        boolean systemBalancesToZero,
        List<TreasuryBalanceDto> treasuryBalances,
        List<AccountReconciliationDto> accounts,
        boolean allReconciled
) {}
