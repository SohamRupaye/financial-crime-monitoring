package com.sohamrupaye.financialcrimemonitoring.repository;

import com.sohamrupaye.financialcrimemonitoring.TestcontainersConfiguration;
import com.sohamrupaye.financialcrimemonitoring.config.JpaAuditingConfig;
import com.sohamrupaye.financialcrimemonitoring.model.Account;
import com.sohamrupaye.financialcrimemonitoring.model.Customer;
import com.sohamrupaye.financialcrimemonitoring.model.RiskAssessment;
import com.sohamrupaye.financialcrimemonitoring.model.RiskRuleResult;
import com.sohamrupaye.financialcrimemonitoring.model.Transaction;
import com.sohamrupaye.financialcrimemonitoring.model.enums.AccountStatus;
import com.sohamrupaye.financialcrimemonitoring.model.enums.AccountType;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class RiskAssessmentRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-09-01T10:00:00Z");

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

    private Transaction transaction;

    @BeforeEach
    void persistTransaction() {
        Customer customer = customerRepository.save(new Customer("CUST-A0000001", "Asha", "Menon",
                "asha@example.com", LocalDate.of(1990, 1, 1), "IN", RiskLevel.HIGH));

        Account account = accountRepository.save(new Account("ACC-000000000001", customer, "INR",
                BigDecimal.ZERO, LocalDate.now(), AccountType.SAVINGS, AccountStatus.ACTIVE));

        transaction = transactionRepository.save(new Transaction("TXN-00000001", account,
                TransactionType.TRANSFER, new BigDecimal("485000.0000"), "INR",
                "ACC-EXTERNAL", "XA", NOW));
    }

    private RiskAssessment assessmentWith(int score, RiskLevel level, RiskRuleResult... results) {
        RiskAssessment assessment = new RiskAssessment(transaction, score, level, NOW);
        assessment.record(score, level, NOW, List.of(results));
        return riskAssessmentRepository.save(assessment);
    }

    @Test
    @DisplayName("an assessment saves with its rule results attached")
    void saveCascadesToRuleResults() {
        assessmentWith(45, RiskLevel.MEDIUM,
                new RiskRuleResult(RuleCode.LARGE_AMOUNT, true, 25, "over the threshold"),
                new RiskRuleResult(RuleCode.VELOCITY, false, 0, null));

        entityManager.flush();
        entityManager.clear();

        RiskAssessment found = riskAssessmentRepository
                .findByTransaction_TransactionReference("TXN-00000001")
                .orElseThrow();

        assertThat(found.getScore()).isEqualTo(45);
        assertThat(found.getRiskLevel()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(found.getRuleResults()).hasSize(2);
        // Untriggered results survive the round-trip with a null reason.
        assertThat(found.getRuleResults())
                .filteredOn(result -> result.getRuleCode() == RuleCode.VELOCITY)
                .singleElement()
                .satisfies(quiet -> assertThat(quiet.getReason()).isNull());
    }

    @Test
    @DisplayName("all results round-trip, whatever order the database returns them in")
    void ruleResultsRoundTrip() {
        assessmentWith(75, RiskLevel.HIGH,
                new RiskRuleResult(RuleCode.COUNTRY_RISK, true, 20, "watched country"),
                new RiskRuleResult(RuleCode.LARGE_AMOUNT, true, 25, "over the threshold"),
                new RiskRuleResult(RuleCode.CUSTOMER_RISK, true, 30, "elevated customer"));

        entityManager.flush();
        entityManager.clear();

        assertThat(riskAssessmentRepository
                .findByTransaction_TransactionReference("TXN-00000001")
                .orElseThrow()
                .getRuleResults())
                // Deliberately order-insensitive: ordering the explanation is the
                // mapper's job, not the repository's.
                .extracting(RiskRuleResult::getRuleCode)
                .containsExactlyInAnyOrder(RuleCode.CUSTOMER_RISK, RuleCode.COUNTRY_RISK,
                        RuleCode.LARGE_AMOUNT);
    }

    @Test
    @DisplayName("re-recording deletes the previous results rather than orphaning them")
    void reRecordingRemovesOldResults() {
        RiskAssessment assessment = assessmentWith(25, RiskLevel.LOW,
                new RiskRuleResult(RuleCode.LARGE_AMOUNT, true, 25, "stale reason"));
        entityManager.flush();

        assessment.record(20, RiskLevel.LOW, NOW.plusSeconds(60),
                List.of(new RiskRuleResult(RuleCode.VELOCITY, true, 20, "fresh reason")));
        riskAssessmentRepository.save(assessment);

        entityManager.flush();
        entityManager.clear();

        RiskAssessment reloaded = riskAssessmentRepository
                .findByTransaction_TransactionReference("TXN-00000001")
                .orElseThrow();

        // orphanRemoval is what makes this one row rather than two. Without it the
        // old result would linger with a null parent and fail the not-null column.
        assertThat(reloaded.getRuleResults()).hasSize(1);
        assertThat(reloaded.getRuleResults().get(0).getReason()).isEqualTo("fresh reason");
        assertThat(reloaded.getScore()).isEqualTo(20);
    }

    @Test
    @DisplayName("a transaction cannot have two assessments")
    void oneAssessmentPerTransaction() {
        assessmentWith(45, RiskLevel.MEDIUM,
                new RiskRuleResult(RuleCode.LARGE_AMOUNT, true, 25, "over the threshold"));
        entityManager.flush();

        RiskAssessment duplicate = new RiskAssessment(transaction, 60, RiskLevel.HIGH, NOW);
        duplicate.record(60, RiskLevel.HIGH, NOW,
                List.of(new RiskRuleResult(RuleCode.VELOCITY, true, 20, "burst")));

        // The unique constraint on transaction_id is what forces re-assessment to
        // update rather than insert.
        assertThatThrownBy(() -> {
            riskAssessmentRepository.save(duplicate);
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("the score column refuses a value outside its own scale")
    void scoreIsConstrainedToItsRange() {
        RiskAssessment assessment = new RiskAssessment(transaction, 140, RiskLevel.CRITICAL, NOW);
        assessment.record(140, RiskLevel.CRITICAL, NOW,
                List.of(new RiskRuleResult(RuleCode.LARGE_AMOUNT, true, 140, "impossible")));

        // Belt and braces behind the cap in RiskScorer: if capping ever regressed,
        // the database would catch it rather than storing a meaningless 140.
        assertThatThrownBy(() -> {
            riskAssessmentRepository.save(assessment);
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}
