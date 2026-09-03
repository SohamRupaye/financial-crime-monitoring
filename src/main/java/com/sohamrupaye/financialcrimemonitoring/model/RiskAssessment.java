package com.sohamrupaye.financialcrimemonitoring.model;

import com.sohamrupaye.financialcrimemonitoring.model.enums.RiskLevel;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.sohamrupaye.financialcrimemonitoring.rules.RuleCode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What the rules engine concluded about one transaction, and why.
 *
 * <p>The "why" is the point. A bare score gives an analyst nothing to act on and
 * nothing to justify a decision with, so the individual rule results are stored
 * alongside it — including the ones that did not fire.
 */
@Entity
@Table(
        name = "risk_assessments",
        indexes = {
                @Index(name = "idx_risk_assessments_risk_level", columnList = "risk_level"),
                @Index(name = "idx_risk_assessments_score", columnList = "score")
        }
)
@Getter
@NoArgsConstructor
public class RiskAssessment extends BaseEntity {

    /**
     * One assessment per transaction, enforced by a unique constraint on the
     * column. Re-evaluating replaces this one rather than adding a version — see
     * the limitations section of the readme.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, unique = true)
    private Transaction transaction;

    @Column(nullable = false)
    private int score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RiskLevel riskLevel;

    /** When the rules last ran, as distinct from when the row was first written. */
    @Column(nullable = false)
    private Instant assessedAt;

    /**
     * {@code orphanRemoval} is what lets a result that is no longer produced be
     * dropped from the list and deleted rather than orphaned with a null parent.
     */
    /**
     * No {@code @OrderBy}: the column stores the enum name, so ordering by it
     * would sort alphabetically — COUNTRY_RISK ahead of CUSTOMER_RISK — and a
     * reloaded assessment would list its reasons in a different order from a
     * freshly scored one. Ordering belongs to the mapper, which is where the API
     * contract is shaped.
     */
    @OneToMany(mappedBy = "riskAssessment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RiskRuleResult> ruleResults = new ArrayList<>();

    public RiskAssessment(Transaction transaction, int score, RiskLevel riskLevel,
                          Instant assessedAt) {
        this.transaction = transaction;
        this.score = score;
        this.riskLevel = riskLevel;
        this.assessedAt = assessedAt;
    }

    public List<RiskRuleResult> getRuleResults() {
        return Collections.unmodifiableList(ruleResults);
    }

    /**
     * Sets the score and every rule result in one go.
     *
     * <p>A single method rather than setters because these four things are only
     * ever meaningful together — a score without the results that produced it is
     * exactly the unexplainable number this class exists to avoid.
     *
     * <p>Results are reconciled by rule code rather than cleared and re-added.
     * Clearing looks simpler and does not work: Hibernate orders inserts before
     * deletes within a flush, so re-assessing a transaction would insert a second
     * row for a rule code it already has and break the unique constraint on
     * {@code (risk_assessment_id, rule_code)}. Matching on the code makes
     * re-assessment an update, which is also what it actually is.
     */
    public void record(int score, RiskLevel riskLevel, Instant assessedAt,
                       List<RiskRuleResult> results) {
        this.score = score;
        this.riskLevel = riskLevel;
        this.assessedAt = assessedAt;

        Map<RuleCode, RiskRuleResult> existing = new EnumMap<>(RuleCode.class);
        this.ruleResults.forEach(result -> existing.put(result.getRuleCode(), result));

        results.forEach(fresh -> {
            RiskRuleResult current = existing.remove(fresh.getRuleCode());

            if (current != null) {
                current.overwriteWith(fresh);
            } else {
                fresh.attachTo(this);
                this.ruleResults.add(fresh);
            }
        });

        // Whatever is left was produced by a rule that no longer reports - one
        // that has been removed, or renamed. orphanRemoval deletes these.
        this.ruleResults.removeAll(existing.values());
    }

    public boolean isAtOrAbove(int threshold) {
        return score >= threshold;
    }
}
