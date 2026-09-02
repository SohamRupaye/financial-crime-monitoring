package com.sohamrupaye.financialcrimemonitoring.dto;

import com.sohamrupaye.financialcrimemonitoring.model.enums.RiskLevel;

import java.time.Instant;
import java.time.LocalDate;

/**
 * What the API returns for a customer.
 *
 * <p>Separate from {@link CreateCustomerRequest} because reads and writes are not
 * symmetric: this exposes {@code customerReference} and {@code riskLevel}, which
 * the server owns and a client may not set.
 *
 * <p>Returning the entity instead would make every column public API, and
 * serialising it would drag in the accounts, their transactions and every alert
 * attached to them.
 */
public record CustomerResponse(
        String customerReference,
        String firstName,
        String lastName,
        String fullName,
        String email,
        LocalDate dateOfBirth,
        String countryCode,
        RiskLevel riskLevel,
        Instant createdAt
) {
}
