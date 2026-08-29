package com.sohamrupaye.financialcrimemonitoring.dto;

import com.sohamrupaye.financialcrimemonitoring.model.enums.RiskLevel;

import java.time.Instant;
import java.util.List;

/**
 * An explainable risk assessment.
 *
 * <p>{@code reasons} is the flat list of what fired, in rule-code order, so a
 * client can render the explanation without walking the detail. {@code rules}
 * carries the full picture including the rules that stayed quiet. The overlap is
 * intentional — the common case should not require the caller to filter.
 */
public record RiskAssessmentResponse(
        String transactionReference,
        int score,
        RiskLevel level,
        Instant assessedAt,
        List<String> reasons,
        List<RuleResultResponse> rules
) {
}
