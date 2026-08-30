package com.sohamrupaye.financialcrimemonitoring.service;

import com.sohamrupaye.financialcrimemonitoring.model.Alert;
import com.sohamrupaye.financialcrimemonitoring.model.RiskAssessment;
import com.sohamrupaye.financialcrimemonitoring.repository.AlertRepository;
import com.sohamrupaye.financialcrimemonitoring.rules.AmlProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Raises alerts for assessments that need a person to look at them.
 */
@Service
@Transactional(readOnly = true)
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private static final String REFERENCE_PREFIX = "ALRT-";

    private final AlertRepository alertRepository;
    private final int alertThreshold;

    public AlertService(AlertRepository alertRepository, AmlProperties properties) {
        this.alertRepository = alertRepository;
        this.alertThreshold = properties.alerting().threshold();
    }

    /**
     * Raises an alert if the assessment warrants one and does not already have
     * one.
     *
     * <p>Two things this deliberately does not do. It never raises a second alert
     * for the same assessment, and it never withdraws an existing one when a
     * re-score comes back lower. An alert that has been raised may already be
     * assigned and part-investigated; quietly deleting it because a threshold
     * moved would destroy an analyst's work and the audit trail with it.
     * Disagreeing with a raised alert is what {@code FALSE_POSITIVE} is for.
     */
    @Transactional
    public Optional<Alert> raiseIfNeeded(RiskAssessment assessment) {
        if (!assessment.isAtOrAbove(alertThreshold)) {
            return Optional.empty();
        }

        if (alertRepository.existsByRiskAssessmentId(assessment.getId())) {
            return Optional.empty();
        }

        Alert alert = alertRepository.save(new Alert(generateReference(), assessment));

        log.info("Raised alert {} for transaction {} scoring {} ({})",
                alert.getAlertReference(),
                assessment.getTransaction().getTransactionReference(),
                assessment.getScore(),
                assessment.getRiskLevel());

        return Optional.of(alert);
    }

    private String generateReference() {
        return REFERENCE_PREFIX + UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase(Locale.ROOT);
    }
}
