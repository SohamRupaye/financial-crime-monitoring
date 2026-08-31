package com.sohamrupaye.financialcrimemonitoring.mapper;

import com.sohamrupaye.financialcrimemonitoring.dto.AlertResponse;
import com.sohamrupaye.financialcrimemonitoring.dto.AlertSummaryResponse;
import com.sohamrupaye.financialcrimemonitoring.dto.RiskAssessmentResponse;
import com.sohamrupaye.financialcrimemonitoring.model.Alert;
import com.sohamrupaye.financialcrimemonitoring.model.RiskAssessment;
import com.sohamrupaye.financialcrimemonitoring.model.Transaction;

public final class AlertMapper {

    private AlertMapper() {
    }

    /** Must run inside a transaction — the whole graph below the alert is lazy. */
    public static AlertResponse toResponse(Alert alert) {
        RiskAssessment assessment = alert.getRiskAssessment();
        Transaction transaction = assessment.getTransaction();

        // Reuses the assessment mapper rather than re-deriving the reasons, so the
        // two endpoints cannot drift apart on ordering or on which rules count.
        RiskAssessmentResponse assessed = RiskAssessmentMapper.toResponse(assessment);

        return new AlertResponse(
                alert.getAlertReference(),
                alert.getStatus(),
                alert.getCreatedAt(),
                alert.getUpdatedAt(),

                assessed.score(),
                assessed.level(),
                assessed.assessedAt(),
                assessed.reasons(),
                assessed.rules(),

                transaction.getTransactionReference(),
                transaction.getTransactionType(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getCounterpartyAccountNumber(),
                transaction.getCounterpartyCountry(),
                transaction.getOccurredAt(),

                transaction.getAccount().getAccountNumber(),
                transaction.getAccount().getCustomer().getCustomerReference(),
                transaction.getAccount().getCustomer().getRiskLevel());
    }

    /** No rule results — see {@link AlertSummaryResponse} for why. */
    public static AlertSummaryResponse toSummary(Alert alert) {
        RiskAssessment assessment = alert.getRiskAssessment();
        Transaction transaction = assessment.getTransaction();

        return new AlertSummaryResponse(
                alert.getAlertReference(),
                alert.getStatus(),
                alert.getCreatedAt(),
                assessment.getScore(),
                assessment.getRiskLevel(),
                transaction.getTransactionReference(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getAccount().getAccountNumber(),
                transaction.getAccount().getCustomer().getCustomerReference());
    }
}
