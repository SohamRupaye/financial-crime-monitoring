package com.sohamrupaye.financialcrimemonitoring.service;

import com.sohamrupaye.financialcrimemonitoring.dto.RiskAssessmentResponse;
import com.sohamrupaye.financialcrimemonitoring.exception.ResourceNotFoundException;
import com.sohamrupaye.financialcrimemonitoring.model.Account;
import com.sohamrupaye.financialcrimemonitoring.model.Customer;
import com.sohamrupaye.financialcrimemonitoring.model.RiskAssessment;
import com.sohamrupaye.financialcrimemonitoring.model.RiskRuleResult;
import com.sohamrupaye.financialcrimemonitoring.model.Transaction;
import com.sohamrupaye.financialcrimemonitoring.model.enums.AccountStatus;
import com.sohamrupaye.financialcrimemonitoring.model.enums.AccountType;
import com.sohamrupaye.financialcrimemonitoring.model.enums.RiskLevel;
import com.sohamrupaye.financialcrimemonitoring.model.enums.TransactionType;
import com.sohamrupaye.financialcrimemonitoring.repository.RiskAssessmentRepository;
import com.sohamrupaye.financialcrimemonitoring.repository.TransactionRepository;
import com.sohamrupaye.financialcrimemonitoring.rules.AmlRulesEngine;
import com.sohamrupaye.financialcrimemonitoring.rules.RiskScore;
import com.sohamrupaye.financialcrimemonitoring.rules.RiskScorer;
import com.sohamrupaye.financialcrimemonitoring.rules.RuleCode;
import com.sohamrupaye.financialcrimemonitoring.rules.RuleResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskAssessmentServiceTest {

    private static final String TRANSACTION_REFERENCE = "TXN-93842A1C";
    private static final Instant OCCURRED_AT = Instant.parse("2026-09-01T10:00:00Z");

    @Mock
    private AmlRulesEngine rulesEngine;

    @Mock
    private RiskScorer riskScorer;

    @Mock
    private RiskAssessmentRepository riskAssessmentRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AlertService alertService;

    @InjectMocks
    private RiskAssessmentService riskAssessmentService;

    private Transaction transaction;

    @BeforeEach
    void buildTransaction() {
        Customer customer = new Customer("CUST-3F2A9C41", "Asha", "Menon",
                "asha.menon@example.com", LocalDate.of(1990, 5, 17), "IN", RiskLevel.HIGH);

        Account account = new Account("ACC-9B41C7E20D5A", customer, "INR", BigDecimal.ZERO,
                LocalDate.of(2026, 1, 10), AccountType.SAVINGS, AccountStatus.ACTIVE);

        transaction = new Transaction(TRANSACTION_REFERENCE, account, TransactionType.TRANSFER,
                new BigDecimal("485000.00"), "INR", "ACC-EXTERNAL-8841", "XA", OCCURRED_AT);
    }

    private void givenRules(List<RuleResult> results, int score, RiskLevel level) {
        when(rulesEngine.evaluate(transaction)).thenReturn(results);
        when(riskScorer.score(results)).thenReturn(new RiskScore(score, level));
        when(riskAssessmentRepository.save(any(RiskAssessment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static List<RuleResult> mixedResults() {
        return List.of(
                RuleResult.triggered(RuleCode.LARGE_AMOUNT, 25, "over the threshold"),
                RuleResult.notTriggered(RuleCode.VELOCITY),
                RuleResult.triggered(RuleCode.CUSTOMER_RISK, 20, "elevated customer"));
    }

    @Test
    @DisplayName("assess() stores the score, the level and every rule result")
    void assessStoresEverything() {
        givenRules(mixedResults(), 45, RiskLevel.MEDIUM);
        when(riskAssessmentRepository.findByTransaction_TransactionReference(TRANSACTION_REFERENCE))
                .thenReturn(Optional.empty());

        RiskAssessment assessment = riskAssessmentService.assess(transaction);

        assertThat(assessment.getScore()).isEqualTo(45);
        assertThat(assessment.getRiskLevel()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(assessment.getAssessedAt()).isNotNull();

        // All three, not just the two that fired.
        assertThat(assessment.getRuleResults())
                .extracting(RiskRuleResult::getRuleCode)
                .containsExactlyInAnyOrder(RuleCode.LARGE_AMOUNT, RuleCode.VELOCITY,
                        RuleCode.CUSTOMER_RISK);

        assertThat(assessment.getRuleResults())
                .filteredOn(result -> !result.isTriggered())
                .singleElement()
                .satisfies(quiet -> {
                    assertThat(quiet.getPoints()).isZero();
                    assertThat(quiet.getReason()).isNull();
                });
    }

    @Test
    @DisplayName("assess() links every stored result back to its assessment")
    void assessLinksResultsToParent() {
        givenRules(mixedResults(), 45, RiskLevel.MEDIUM);
        when(riskAssessmentRepository.findByTransaction_TransactionReference(TRANSACTION_REFERENCE))
                .thenReturn(Optional.empty());

        RiskAssessment assessment = riskAssessmentService.assess(transaction);

        // Without this the insert fails on a not-null foreign key, and it is the
        // owning side that has to set it.
        assertThat(assessment.getRuleResults())
                .allSatisfy(result ->
                        assertThat(result.getRiskAssessment()).isSameAs(assessment));
    }

    @Test
    @DisplayName("re-assessing replaces the existing assessment instead of adding one")
    void assessReplacesExistingAssessment() {
        RiskAssessment existing = new RiskAssessment(
                transaction, 25, RiskLevel.LOW, OCCURRED_AT);
        existing.record(25, RiskLevel.LOW, OCCURRED_AT,
                List.of(new RiskRuleResult(RuleCode.LARGE_AMOUNT, true, 25, "stale reason")));

        givenRules(mixedResults(), 45, RiskLevel.MEDIUM);
        when(riskAssessmentRepository.findByTransaction_TransactionReference(TRANSACTION_REFERENCE))
                .thenReturn(Optional.of(existing));

        RiskAssessment assessment = riskAssessmentService.assess(transaction);

        // The same row, updated. One assessment per transaction is a unique
        // constraint, so creating a second would fail on insert.
        assertThat(assessment).isSameAs(existing);
        assertThat(assessment.getScore()).isEqualTo(45);
        assertThat(assessment.getRuleResults()).hasSize(3);
        assertThat(assessment.getRuleResults())
                .extracting(RiskRuleResult::getReason)
                .doesNotContain("stale reason");
    }

    @Test
    @DisplayName("reassess() rejects an unknown transaction reference")
    void reassessRejectsUnknownTransaction() {
        when(transactionRepository.findByTransactionReference("TXN-NOPE"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> riskAssessmentService.reassess("TXN-NOPE"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("TXN-NOPE");

        verify(rulesEngine, never()).evaluate(any());
        verify(riskScorer, never()).score(anyList());
    }

    @Test
    @DisplayName("reassess() returns the freshly scored assessment")
    void reassessReturnsNewAssessment() {
        when(transactionRepository.findByTransactionReference(TRANSACTION_REFERENCE))
                .thenReturn(Optional.of(transaction));
        when(riskAssessmentRepository.findByTransaction_TransactionReference(TRANSACTION_REFERENCE))
                .thenReturn(Optional.empty());
        givenRules(mixedResults(), 45, RiskLevel.MEDIUM);

        RiskAssessmentResponse response = riskAssessmentService.reassess(TRANSACTION_REFERENCE);

        assertThat(response.transactionReference()).isEqualTo(TRANSACTION_REFERENCE);
        assertThat(response.score()).isEqualTo(45);
        // reasons carries only what fired; rules carries all three.
        assertThat(response.reasons()).containsExactly("over the threshold", "elevated customer");
        assertThat(response.rules()).hasSize(3);
    }

    @Test
    @DisplayName("findByTransactionReference() throws when nothing was ever assessed")
    void findThrowsWhenUnassessed() {
        when(riskAssessmentRepository.findByTransaction_TransactionReference("TXN-NOPE"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> riskAssessmentService.findByTransactionReference("TXN-NOPE"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Risk assessment");
    }

    @Test
    @DisplayName("reasons are ordered by rule code however the results arrive")
    void reasonsAreOrderedByRuleCode() {
        // Fed in back to front. Both a fresh assessment and a reloaded one go
        // through the mapper, so this is the one place the order is decided.
        List<RuleResult> reversed = List.of(
                RuleResult.triggered(RuleCode.COUNTRY_RISK, 20, "watched country"),
                RuleResult.triggered(RuleCode.CUSTOMER_RISK, 20, "elevated customer"),
                RuleResult.triggered(RuleCode.LARGE_AMOUNT, 25, "over the threshold"));

        when(transactionRepository.findByTransactionReference(TRANSACTION_REFERENCE))
                .thenReturn(Optional.of(transaction));
        when(riskAssessmentRepository.findByTransaction_TransactionReference(TRANSACTION_REFERENCE))
                .thenReturn(Optional.empty());
        givenRules(reversed, 65, RiskLevel.HIGH);

        RiskAssessmentResponse response = riskAssessmentService.reassess(TRANSACTION_REFERENCE);

        assertThat(response.reasons()).containsExactly(
                "over the threshold", "elevated customer", "watched country");
    }

    @Test
    @DisplayName("assess() hands the stored assessment to the alert service")
    void assessOffersTheAssessmentForAlerting() {
        givenRules(mixedResults(), 45, RiskLevel.MEDIUM);
        when(riskAssessmentRepository.findByTransaction_TransactionReference(TRANSACTION_REFERENCE))
                .thenReturn(Optional.empty());

        RiskAssessment assessment = riskAssessmentService.assess(transaction);

        // Whether it clears the threshold is the alert service's call, not this
        // one's - but it always gets asked, in the same transaction.
        verify(alertService).raiseIfNeeded(assessment);
    }
}
