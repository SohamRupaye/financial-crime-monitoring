package com.sohamrupaye.financialcrimemonitoring.dto;

import com.sohamrupaye.financialcrimemonitoring.model.enums.AlertStatus;
import com.sohamrupaye.financialcrimemonitoring.model.enums.RiskLevel;
import com.sohamrupaye.financialcrimemonitoring.model.enums.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * One alert with everything needed to start investigating it.
 *
 * <p>Flat rather than nested. An analyst opening an alert wants the amount, the
 * counterparty country, whose account it is and why it fired — making them walk
 * {@code alert.assessment.transaction.account.customer} to find any of that
 * serves the object graph rather than the reader.
 */
public record AlertResponse(
        String alertReference,
        AlertStatus status,
        Instant raisedAt,
        Instant updatedAt,

        int score,
        RiskLevel level,
        Instant assessedAt,
        List<String> reasons,
        List<RuleResultResponse> rules,

        String transactionReference,
        TransactionType transactionType,
        BigDecimal amount,
        String currency,
        String counterpartyAccountNumber,
        String counterpartyCountry,
        Instant occurredAt,

        String accountNumber,
        String customerReference,
        RiskLevel customerRiskLevel
) {
}
