package com.sohamrupaye.financialcrimemonitoring.dto;

import com.sohamrupaye.financialcrimemonitoring.model.enums.AlertStatus;
import com.sohamrupaye.financialcrimemonitoring.model.enums.RiskLevel;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * An alert as it appears in a queue.
 *
 * <p>Separate from {@link AlertResponse} because of the rule results. Fetching a
 * collection alongside a page forces Hibernate to paginate in memory — it reads
 * every matching row, then slices — so the list deliberately does without them
 * and the detail view carries them.
 */
public record AlertSummaryResponse(
        String alertReference,
        AlertStatus status,
        Instant raisedAt,
        int score,
        RiskLevel level,
        String transactionReference,
        BigDecimal amount,
        String currency,
        String accountNumber,
        String customerReference
) {
}
