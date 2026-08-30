package com.sohamrupaye.financialcrimemonitoring.service;

import com.sohamrupaye.financialcrimemonitoring.model.Account;
import com.sohamrupaye.financialcrimemonitoring.model.Alert;
import com.sohamrupaye.financialcrimemonitoring.model.Customer;
import com.sohamrupaye.financialcrimemonitoring.model.RiskAssessment;
import com.sohamrupaye.financialcrimemonitoring.model.Transaction;
import com.sohamrupaye.financialcrimemonitoring.model.enums.AccountStatus;
import com.sohamrupaye.financialcrimemonitoring.model.enums.AccountType;
import com.sohamrupaye.financialcrimemonitoring.model.enums.AlertStatus;
import com.sohamrupaye.financialcrimemonitoring.model.enums.RiskLevel;
import com.sohamrupaye.financialcrimemonitoring.model.enums.TransactionType;
import com.sohamrupaye.financialcrimemonitoring.repository.AlertRepository;
import com.sohamrupaye.financialcrimemonitoring.rules.AmlProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-01T10:00:00Z");

    @Mock
    private AlertRepository alertRepository;

    private static AmlProperties properties() {
        return new AmlProperties(
                new AmlProperties.LargeAmount(new BigDecimal("500000"), 25),
                new AmlProperties.Velocity(java.time.Duration.ofMinutes(10), 10, 20),
                new AmlProperties.Structuring(
                        java.time.Duration.ofHours(24), new BigDecimal("400000"), 3, 30),
                new AmlProperties.CustomerRisk(Map.of(RiskLevel.LOW, 0, RiskLevel.MEDIUM, 10,
                        RiskLevel.HIGH, 20, RiskLevel.CRITICAL, 30)),
                new AmlProperties.CountryRisk(java.util.Set.of("XA"), 20),
                new AmlProperties.Scoring(100, Map.of(RiskLevel.LOW, 0, RiskLevel.MEDIUM, 30,
                        RiskLevel.HIGH, 60, RiskLevel.CRITICAL, 80)),
                new AmlProperties.Alerting(60));
    }

    private AlertService alertService() {
        return new AlertService(alertRepository, properties());
    }

    private static RiskAssessment assessment(int score, RiskLevel level) {
        Customer customer = new Customer("CUST-3F2A9C41", "Asha", "Menon",
                "asha.menon@example.com", LocalDate.of(1990, 5, 17), "IN", RiskLevel.HIGH);

        Account account = new Account("ACC-9B41C7E20D5A", customer, "INR", BigDecimal.ZERO,
                LocalDate.of(2026, 1, 10), AccountType.SAVINGS, AccountStatus.ACTIVE);

        Transaction transaction = new Transaction("TXN-93842A1C", account,
                TransactionType.TRANSFER, new BigDecimal("485000.00"), "INR",
                "ACC-EXTERNAL-8841", "XA", NOW);

        return new RiskAssessment(transaction, score, level, NOW);
    }

    @Test
    @DisplayName("an assessment at the threshold raises an OPEN alert")
    void raisesAtThreshold() {
        when(alertRepository.existsByRiskAssessmentId(any())).thenReturn(false);
        when(alertRepository.save(any(Alert.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Exactly 60, the configured threshold. Inclusive, so this alerts.
        Optional<Alert> alert = alertService().raiseIfNeeded(assessment(60, RiskLevel.HIGH));

        assertThat(alert).isPresent();
        assertThat(alert.get().getAlertReference()).startsWith("ALRT-");
        assertThat(alert.get().getStatus()).isEqualTo(AlertStatus.OPEN);
    }

    @Test
    @DisplayName("an assessment below the threshold raises nothing")
    void doesNotRaiseBelowThreshold() {
        Optional<Alert> alert = alertService().raiseIfNeeded(assessment(59, RiskLevel.MEDIUM));

        assertThat(alert).isEmpty();
        verify(alertRepository, never()).save(any());
    }

    @Test
    @DisplayName("re-scoring an already alerted assessment does not raise a second alert")
    void doesNotRaiseTwice() {
        when(alertRepository.existsByRiskAssessmentId(any())).thenReturn(true);

        // One alert per assessment is a unique constraint, so a second save would
        // fail on insert - but the point is that an analyst should see one item in
        // their queue, not one per re-evaluation.
        assertThat(alertService().raiseIfNeeded(assessment(90, RiskLevel.CRITICAL))).isEmpty();

        verify(alertRepository, never()).save(any());
    }

    @Test
    @DisplayName("a lower re-score never withdraws an existing alert")
    void doesNotWithdrawExistingAlerts() {
        // A raised alert may already be assigned and part-investigated. Deleting
        // it because a threshold moved would take an analyst's work with it -
        // disagreeing with an alert is what FALSE_POSITIVE is for.
        alertService().raiseIfNeeded(assessment(10, RiskLevel.LOW));

        verify(alertRepository, never()).delete(any());
        verify(alertRepository, never()).save(any());
    }
}
