package com.sohamrupaye.financialcrimemonitoring.mapper;

import com.sohamrupaye.financialcrimemonitoring.dto.RiskAssessmentResponse;
import com.sohamrupaye.financialcrimemonitoring.dto.RuleResultResponse;
import com.sohamrupaye.financialcrimemonitoring.model.RiskAssessment;
import com.sohamrupaye.financialcrimemonitoring.model.RiskRuleResult;

import java.util.Comparator;
import java.util.List;

public final class RiskAssessmentMapper {

    private RiskAssessmentMapper() {
    }

    /** Must run inside a transaction: the transaction and rule results are lazy. */
    public static RiskAssessmentResponse toResponse(RiskAssessment assessment) {
        // Sorted here rather than relying on the read order. Results arrive in
        // RuleCode order from the engine but in database order on a reload, and an
        // explanation that reshuffles between the two is not much of an
        // explanation.
        List<RuleResultResponse> rules = assessment.getRuleResults().stream()
                .sorted(Comparator.comparing(RiskRuleResult::getRuleCode))
                .map(RiskAssessmentMapper::toResponse)
                .toList();

        List<String> reasons = rules.stream()
                .filter(RuleResultResponse::triggered)
                .map(RuleResultResponse::reason)
                .toList();

        return new RiskAssessmentResponse(
                assessment.getTransaction().getTransactionReference(),
                assessment.getScore(),
                assessment.getRiskLevel(),
                assessment.getAssessedAt(),
                reasons,
                rules);
    }

    private static RuleResultResponse toResponse(RiskRuleResult result) {
        return new RuleResultResponse(result.getRuleCode(), result.isTriggered(),
                result.getPoints(), result.getReason());
    }
}
