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
 * <p>Returning the {@code Customer} entity instead would mean:
 * <ul>
 *   <li>every column becomes public API, so renaming one breaks clients;</li>
 *   <li>lazy associations serialise on access, firing surprise N+1 queries — or
 *       failing outright once {@code open-in-view} is off, as it is here;</li>
 *   <li>the whole graph leaks, since serialising a customer would drag in its
 *       accounts, their transactions, and every alert attached to them.</li>
 * </ul>
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
