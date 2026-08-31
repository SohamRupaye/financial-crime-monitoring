package com.sohamrupaye.financialcrimemonitoring.service;

import com.sohamrupaye.financialcrimemonitoring.dto.AlertResponse;
import com.sohamrupaye.financialcrimemonitoring.dto.AlertSummaryResponse;
import com.sohamrupaye.financialcrimemonitoring.exception.ResourceNotFoundException;
import com.sohamrupaye.financialcrimemonitoring.mapper.AlertMapper;
import com.sohamrupaye.financialcrimemonitoring.model.Alert;
import com.sohamrupaye.financialcrimemonitoring.model.RiskAssessment;
import com.sohamrupaye.financialcrimemonitoring.model.enums.AlertStatus;
import com.sohamrupaye.financialcrimemonitoring.repository.AlertRepository;
import com.sohamrupaye.financialcrimemonitoring.rules.AmlProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    public Page<AlertSummaryResponse> search(AlertStatus status, Pageable pageable) {
        Page<Alert> alerts = status == null
                ? alertRepository.findAll(pageable)
                : alertRepository.findByStatus(status, pageable);

        return alerts.map(AlertMapper::toSummary);
    }

    public AlertResponse findByReference(String alertReference) {
        return AlertMapper.toResponse(requireByReference(alertReference));
    }

    /**
     * Moves an alert through the workflow.
     *
     * <p>The rule about which moves are legal lives on {@link Alert}, not here —
     * it is an invariant of the alert rather than of this method, and putting it
     * in the entity means no other path can bypass it.
     */
    @Transactional
    public AlertResponse updateStatus(String alertReference, AlertStatus target) {
        Alert alert = requireByReference(alertReference);
        AlertStatus previous = alert.getStatus();

        alert.transitionTo(target);

        log.info("Alert {} moved from {} to {}", alertReference, previous, target);

        // No explicit save: the alert is a managed entity inside this transaction,
        // so the change is flushed on commit. Calling save would work and would
        // also suggest it were necessary.
        return AlertMapper.toResponse(alert);
    }

    private Alert requireByReference(String alertReference) {
        return alertRepository.findByAlertReference(alertReference)
                .orElseThrow(() -> ResourceNotFoundException.of("Alert", alertReference));
    }

    private String generateReference() {
        return REFERENCE_PREFIX + UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase(Locale.ROOT);
    }
}
