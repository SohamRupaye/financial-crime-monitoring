package com.sohamrupaye.financialcrimemonitoring.service;

import com.sohamrupaye.financialcrimemonitoring.dto.RiskAssessmentResponse;
import com.sohamrupaye.financialcrimemonitoring.exception.ResourceNotFoundException;
import com.sohamrupaye.financialcrimemonitoring.mapper.RiskAssessmentMapper;
import com.sohamrupaye.financialcrimemonitoring.model.RiskAssessment;
import com.sohamrupaye.financialcrimemonitoring.model.RiskRuleResult;
import com.sohamrupaye.financialcrimemonitoring.model.Transaction;
import com.sohamrupaye.financialcrimemonitoring.repository.RiskAssessmentRepository;
import com.sohamrupaye.financialcrimemonitoring.repository.TransactionRepository;
import com.sohamrupaye.financialcrimemonitoring.rules.AmlRulesEngine;
import com.sohamrupaye.financialcrimemonitoring.rules.RiskScore;
import com.sohamrupaye.financialcrimemonitoring.rules.RiskScorer;
import com.sohamrupaye.financialcrimemonitoring.rules.RuleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Runs the rules, scores the result and stores it.
 *
 * <p>Where the three pieces meet: {@link AmlRulesEngine} gathers evidence,
 * {@link RiskScorer} weighs it, and this class is what makes the outcome durable.
 * None of the three knows about HTTP.
 */
@Service
@Transactional(readOnly = true)
public class RiskAssessmentService {

    private static final Logger log = LoggerFactory.getLogger(RiskAssessmentService.class);

    private final AmlRulesEngine rulesEngine;
    private final RiskScorer riskScorer;
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final TransactionRepository transactionRepository;
    private final AlertService alertService;

    public RiskAssessmentService(AmlRulesEngine rulesEngine,
                                 RiskScorer riskScorer,
                                 RiskAssessmentRepository riskAssessmentRepository,
                                 TransactionRepository transactionRepository,
                                 AlertService alertService) {
        this.rulesEngine = rulesEngine;
        this.riskScorer = riskScorer;
        this.riskAssessmentRepository = riskAssessmentRepository;
        this.transactionRepository = transactionRepository;
        this.alertService = alertService;
    }

    /**
     * Assesses a transaction, creating or replacing its assessment.
     *
     * <p>Re-evaluation is a real operation, not a mistake to guard against: rule
     * thresholds get tuned, and the question "what would this transaction score
     * under the current rules" has to be answerable. It overwrites rather than
     * versioning, which the readme records as a limitation.
     */
    @Transactional
    public RiskAssessment assess(Transaction transaction) {
        List<RuleResult> results = rulesEngine.evaluate(transaction);
        RiskScore riskScore = riskScorer.score(results);

        List<RiskRuleResult> stored = results.stream()
                .map(result -> new RiskRuleResult(
                        result.code(), result.triggered(), result.points(), result.reason()))
                .toList();

        RiskAssessment assessment = riskAssessmentRepository
                .findByTransaction_TransactionReference(transaction.getTransactionReference())
                .orElseGet(() -> new RiskAssessment(
                        transaction, riskScore.score(), riskScore.level(), Instant.now()));

        assessment.record(riskScore.score(), riskScore.level(), Instant.now(), stored);

        RiskAssessment saved = riskAssessmentRepository.save(assessment);

        log.info("Assessed transaction {} at {} ({}), {} of {} rules triggered",
                transaction.getTransactionReference(), riskScore.score(), riskScore.level(),
                results.stream().filter(RuleResult::triggered).count(), results.size());

        // Same transaction boundary as the assessment, so an assessment worth
        // alerting on cannot be stored without its alert.
        alertService.raiseIfNeeded(saved);

        return saved;
    }

    /** Re-assesses by reference, for when rule configuration has changed. */
    @Transactional
    public RiskAssessmentResponse reassess(String transactionReference) {
        Transaction transaction = transactionRepository
                .findByTransactionReference(transactionReference)
                .orElseThrow(() ->
                        ResourceNotFoundException.of("Transaction", transactionReference));

        return RiskAssessmentMapper.toResponse(assess(transaction));
    }

    public RiskAssessmentResponse findByTransactionReference(String transactionReference) {
        return riskAssessmentRepository
                .findByTransaction_TransactionReference(transactionReference)
                .map(RiskAssessmentMapper::toResponse)
                .orElseThrow(() ->
                        ResourceNotFoundException.of("Risk assessment for transaction",
                                transactionReference));
    }
}
