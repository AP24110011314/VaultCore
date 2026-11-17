package com.vaultcore.wallet.dto.response;

import java.util.UUID;

public record UserResponseDto(
        UUID userId,
        String name,
        String email
) {}
