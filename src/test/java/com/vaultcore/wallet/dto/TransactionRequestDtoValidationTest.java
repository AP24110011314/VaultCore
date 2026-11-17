package com.vaultcore.wallet.dto;

import com.vaultcore.wallet.dto.request.TransactionRequestDto;
import com.vaultcore.wallet.enums.TransactionType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionRequestDtoValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void validRequestProducesNoViolations() {
        TransactionRequestDto dto = new TransactionRequestDto(
                TransactionType.TOPUP,
                "aditisingh@gmail.com",
                "GOLD_COINS",
                100L,
                "key-123"
        );
        Set<ConstraintViolation<TransactionRequestDto>> violations = validator.validate(dto);
        assertThat(violations).isEmpty();
    }

    @Test
    void nullTypeFails() {
        TransactionRequestDto dto = new TransactionRequestDto(
                null,
                "aditisingh@gmail.com",
                "GOLD_COINS",
                100L,
                "key-123"
        );
        Set<ConstraintViolation<TransactionRequestDto>> violations = validator.validate(dto);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("type")
                && v.getMessage().equals("Transaction type is required"));
    }

    @Test
    void blankEmailFails() {
        TransactionRequestDto dto = new TransactionRequestDto(
                TransactionType.TOPUP,
                "   ",
                "GOLD_COINS",
                100L,
                "key-123"
        );
        Set<ConstraintViolation<TransactionRequestDto>> violations = validator.validate(dto);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @Test
    void invalidEmailFormatFails() {
        TransactionRequestDto dto = new TransactionRequestDto(
                TransactionType.TOPUP,
                "not-an-email",
                "GOLD_COINS",
                100L,
                "key-123"
        );
        Set<ConstraintViolation<TransactionRequestDto>> violations = validator.validate(dto);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email")
                && v.getMessage().equals("Invalid email format"));
    }

    @Test
    void blankAssetCodeFails() {
        TransactionRequestDto dto = new TransactionRequestDto(
                TransactionType.TOPUP,
                "aditisingh@gmail.com",
                "",
                100L,
                "key-123"
        );
        Set<ConstraintViolation<TransactionRequestDto>> violations = validator.validate(dto);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("assetCode")
                && v.getMessage().equals("Asset code is required"));
    }

    @Test
    void nullAmountFails() {
        TransactionRequestDto dto = new TransactionRequestDto(
                TransactionType.TOPUP,
                "aditisingh@gmail.com",
                "GOLD_COINS",
                null,
                "key-123"
        );
        Set<ConstraintViolation<TransactionRequestDto>> violations = validator.validate(dto);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("amount")
                && v.getMessage().equals("Amount is required"));
    }

    @Test
    void zeroOrNegativeAmountFails() {
        TransactionRequestDto zero = new TransactionRequestDto(
                TransactionType.TOPUP,
                "aditisingh@gmail.com",
                "GOLD_COINS",
                0L,
                "key-123"
        );
        assertThat(validator.validate(zero)).anyMatch(v -> v.getPropertyPath().toString().equals("amount")
                && v.getMessage().equals("Amount must be greater than 0"));

        TransactionRequestDto negative = new TransactionRequestDto(
                TransactionType.TOPUP,
                "aditisingh@gmail.com",
                "GOLD_COINS",
                -10L,
                "key-123"
        );
        assertThat(validator.validate(negative)).anyMatch(v -> v.getPropertyPath().toString().equals("amount")
                && v.getMessage().equals("Amount must be greater than 0"));
    }

    @Test
    void blankOrLongIdempotencyKeyFails() {
        TransactionRequestDto blank = new TransactionRequestDto(
                TransactionType.TOPUP,
                "aditisingh@gmail.com",
                "GOLD_COINS",
                100L,
                " "
        );
        assertThat(validator.validate(blank)).anyMatch(v -> v.getPropertyPath().toString().equals("idempotencyKey"));

        String tooLong = "a".repeat(101);
        TransactionRequestDto tooLongKey = new TransactionRequestDto(
                TransactionType.TOPUP,
                "aditisingh@gmail.com",
                "GOLD_COINS",
                100L,
                tooLong
        );
        assertThat(validator.validate(tooLongKey)).anyMatch(v -> v.getPropertyPath().toString().equals("idempotencyKey")
                && v.getMessage().equals("Idempotency key must be between 1 and 100 characters"));
    }
}
