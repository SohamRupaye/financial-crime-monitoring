package com.sohamrupaye.financialcrimemonitoring.dto;

import com.sohamrupaye.financialcrimemonitoring.model.enums.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * What the API returns for a transaction.
 *
 * <p>The owning account and customer are flattened to their reference strings.
 * An analyst reading a transaction always wants to know whose it is, and one
 * string each is cheaper than nesting two objects.
 */
public record TransactionResponse(
        String transactionReference,
        String accountNumber,
        String customerReference,
        TransactionType transactionType,
        BigDecimal amount,
        String currency,
        String counterpartyAccountNumber,
        String counterpartyCountry,
        Instant occurredAt,
        Instant ingestedAt
) {
}
