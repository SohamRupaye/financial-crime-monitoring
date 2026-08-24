package com.sohamrupaye.financialcrimemonitoring.dto;

import com.sohamrupaye.financialcrimemonitoring.model.enums.TransactionType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A transaction as reported to the monitoring system.
 *
 * <p>No reference and no risk fields: the reference is server-generated, and the
 * score is something we calculate, never something a caller asserts.
 */
public record CreateTransactionRequest(

        @NotBlank(message = "account number is required")
        String accountNumber,

        @NotNull(message = "transaction type is required")
        TransactionType transactionType,

        @NotNull(message = "amount is required")
        @Positive(message = "amount must be greater than zero")
        // Matches NUMERIC(19,4). Without this, an amount with five decimal places
        // is silently rounded on insert rather than rejected.
        @Digits(integer = 15, fraction = 4,
                message = "amount must have at most 15 integer and 4 decimal digits")
        BigDecimal amount,

        @NotBlank(message = "currency is required")
        @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be ISO 4217, e.g. INR")
        String currency,

        // Optional: the counterparty is often at another institution, and cash
        // deposits and withdrawals have none at all.
        @Size(max = 34, message = "counterparty account number must be at most 34 characters")
        String counterpartyAccountNumber,

        @NotBlank(message = "counterparty country is required")
        @Pattern(regexp = "^[A-Z]{2}$", message = "counterparty country must be ISO 3166-1 alpha-2")
        String counterpartyCountry,

        @NotNull(message = "occurredAt is required")
        @PastOrPresent(message = "occurredAt cannot be in the future")
        Instant occurredAt
) {
}
