package com.sohamrupaye.financialcrimemonitoring.dto;

import com.sohamrupaye.financialcrimemonitoring.rules.RuleCode;

/**
 * One rule's verdict as returned by the API, triggered or not.
 *
 * <p>The quiet rules are included deliberately: "the velocity rule looked and
 * found nothing" is information an analyst needs, and its absence is what makes
 * a score feel arbitrary.
 */
public record RuleResultResponse(
        RuleCode code,
        boolean triggered,
        int points,
        String reason
) {
}
