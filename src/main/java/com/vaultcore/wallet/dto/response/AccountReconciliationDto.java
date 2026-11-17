package com.vaultcore.wallet.dto.response;

import com.vaultcore.wallet.enums.AccountType;
import java.util.UUID;

public record AccountReconciliationDto(
        UUID accountId,
        AccountType accountType,
        String owner,
        String assetCode,
        String assetName,
        long cachedBalance,
        long ledgerBalance,
        boolean reconciled
) {}
