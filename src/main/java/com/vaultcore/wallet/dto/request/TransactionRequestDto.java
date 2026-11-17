package com.vaultcore.wallet.dto.request;

import com.vaultcore.wallet.enums.TransactionType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record TransactionRequestDto(
        @NotNull(message = "Transaction type is required")
        TransactionType type,

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @NotBlank(message = "Asset code is required")
        String assetCode,

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be greater than 0")
        Long amount,

        @NotBlank(message = "Idempotency key is required")
        @Size(min = 1, max = 100, message = "Idempotency key must be between 1 and 100 characters")
        String idempotencyKey
) {
}
