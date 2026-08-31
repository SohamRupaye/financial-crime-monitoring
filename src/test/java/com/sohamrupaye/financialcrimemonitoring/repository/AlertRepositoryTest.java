package com.sohamrupaye.financialcrimemonitoring.repository;

import com.sohamrupaye.financialcrimemonitoring.TestcontainersConfiguration;
import com.sohamrupaye.financialcrimemonitoring.config.JpaAuditingConfig;
import com.sohamrupaye.financialcrimemonitoring.model.Account;
import com.sohamrupaye.financialcrimemonitoring.model.Alert;
import com.sohamrupaye.financialcrimemonitoring.model.Customer;
import com.sohamrupaye.financialcrimemonitoring.model.RiskAssessment;
import com.sohamrupaye.financialcrimemonitoring.model.RiskRuleResult;
import com.sohamrupaye.financialcrimemonitoring.model.Transaction;
import com.sohamrupaye.financialcrimemonitoring.model.enums.AccountStatus;
import com.sohamrupaye.financialcrimemonitoring.model.enums.AccountType;
import com.sohamrupaye.financialcrimemonitoring.model.enums.AlertStatus;
import com.sohamrupaye.financialcrimemonitoring.model.enums.RiskLevel;
import com.sohamrupaye.financialcrimemonitoring.model.enums.TransactionType;
import com.sohamrupaye.financialcrimemonitoring.rules.RuleCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class AlertRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-09-01T10:00:00Z");

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private RiskAssessmentRepository riskAssessmentRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private EntityManager entityManager;

    private Account account;

    @BeforeEach
    void persistAccount() {
        Customer customer = customerRepository.save(new Customer("CUST-A0000001", "Asha", "Menon",
                "asha@example.com", LocalDate.of(1990, 1, 1), "IN", RiskLevel.HIGH));

        account = accountRepository.save(new Account("ACC-000000000001", customer, "INR",
                BigDecimal.ZERO, LocalDate.now(), AccountType.SAVINGS, AccountStatus.ACTIVE));
    }

    private RiskAssessment assessment(String transactionReference, int score, RiskLevel level) {
        Transaction transaction = transactionRepository.save(new Transaction(transactionReference,
                account, TransactionType.TRANSFER, new BigDecimal("485000.0000"), "INR",
                "ACC-EXTERNAL", "XA", NOW));

        RiskAssessment assessment = new RiskAssessment(transaction, score, level, NOW);
        assessment.record(score, level, NOW, List.of(
                new RiskRuleResult(RuleCode.LARGE_AMOUNT, true, 25, "over the threshold"),
                new RiskRuleResult(RuleCode.VELOCITY, false, 0, null)));

        return riskAssessmentRepository.save(assessment);
    }

    @Test
    @DisplayName("an alert loads its whole chain down to the customer in one go")
    void findByReferenceFetchesTheGraph() {
        alertRepository.save(new Alert("ALRT-00000001",
                assessment("TXN-00000001", 85, RiskLevel.CRITICAL)));

        entityManager.flush();
        entityManager.clear();

        Alert found = alertRepository.findByAlertReference("ALRT-00000001").orElseThrow();

        // Every hop here is a lazy association. If the entity graph were missing,
        // this would still pass inside the test transaction - just with five
        // separate selects behind it.
        assertThat(found.getStatus()).isEqualTo(AlertStatus.OPEN);
        assertThat(found.getRiskAssessment().getScore()).isEqualTo(85);
        assertThat(found.getRiskAssessment().getRuleResults()).hasSize(2);
        assertThat(found.getRiskAssessment().getTransaction()
                .getAccount().getCustomer().getCustomerReference()).isEqualTo("CUST-A0000001");
    }

    @Test
    @DisplayName("existsByRiskAssessmentId() is what stops a second alert being raised")
    void existsByRiskAssessmentId() {
        RiskAssessment assessed = assessment("TXN-00000002", 85, RiskLevel.CRITICAL);
        alertRepository.save(new Alert("ALRT-00000002", assessed));
        entityManager.flush();

        assertThat(alertRepository.existsByRiskAssessmentId(assessed.getId())).isTrue();
    }

    @Test
    @DisplayName("one assessment cannot carry two alerts")
    void oneAlertPerAssessment() {
        RiskAssessment assessed = assessment("TXN-00000003", 85, RiskLevel.CRITICAL);
        alertRepository.save(new Alert("ALRT-00000003", assessed));
        entityManager.flush();

        // The constraint behind the exists check above: even if the service check
        // were bypassed, the database would refuse.
        assertThatThrownBy(() -> {
            alertRepository.save(new Alert("ALRT-00000004", assessed));
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("findByStatus() returns only that part of the queue")
    void findByStatus() {
        alertRepository.save(new Alert("ALRT-00000005",
                assessment("TXN-00000005", 85, RiskLevel.CRITICAL)));

        Alert acknowledged = alertRepository.save(new Alert("ALRT-00000006",
                assessment("TXN-00000006", 70, RiskLevel.HIGH)));
        acknowledged.transitionTo(AlertStatus.ACKNOWLEDGED);

        entityManager.flush();
        entityManager.clear();

        assertThat(alertRepository.findByStatus(AlertStatus.OPEN, PageRequest.of(0, 20)))
                .extracting(Alert::getAlertReference)
                .containsExactly("ALRT-00000005");

        assertThat(alertRepository.findByStatus(AlertStatus.ACKNOWLEDGED, PageRequest.of(0, 20)))
                .extracting(Alert::getAlertReference)
                .containsExactly("ALRT-00000006");
    }

    @Test
    @DisplayName("a status change is flushed without an explicit save")
    void statusChangeIsFlushedByDirtyChecking() {
        Alert alert = alertRepository.save(new Alert("ALRT-00000007",
                assessment("TXN-00000007", 85, RiskLevel.CRITICAL)));
        entityManager.flush();

        alert.transitionTo(AlertStatus.ACKNOWLEDGED);

        entityManager.flush();
        entityManager.clear();

        // No save call anywhere above. This is what lets AlertService.updateStatus
        // mutate the entity and stop.
        assertThat(alertRepository.findByAlertReference("ALRT-00000007").orElseThrow().getStatus())
                .isEqualTo(AlertStatus.ACKNOWLEDGED);
    }
}
