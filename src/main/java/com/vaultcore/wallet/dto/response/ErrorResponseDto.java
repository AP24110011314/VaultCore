package com.vaultcore.wallet.dto.response;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public record ErrorResponseDto(
        String error,
        String message,
        Instant timestamp
) {
    public ErrorResponseDto(String error, String message) {
        this(error, message, Instant.now().truncatedTo(ChronoUnit.MICROS));
    }
}
