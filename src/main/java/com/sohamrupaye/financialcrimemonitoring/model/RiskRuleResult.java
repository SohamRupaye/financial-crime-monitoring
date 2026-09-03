package com.sohamrupaye.financialcrimemonitoring.model;

import com.sohamrupaye.financialcrimemonitoring.rules.RuleCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One rule's verdict, stored.
 *
 * <p>Untriggered results are kept as well as triggered ones. Without them there
 * is no way to answer "why was this not alerted on", and no data to tune a
 * threshold against — which is most of what a compliance function actually
 * spends its time doing.
 */
@Entity
@Table(name = "risk_rule_results")
@Getter
@NoArgsConstructor
public class RiskRuleResult extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "risk_assessment_id", nullable = false)
    private RiskAssessment riskAssessment;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_code", nullable = false, length = 30)
    private RuleCode ruleCode;

    @Column(nullable = false)
    private boolean triggered;

    @Column(nullable = false)
    private int points;

    /** Null when the rule did not fire — there is nothing to explain. */
    @Column(length = 500)
    private String reason;

    public RiskRuleResult(RuleCode ruleCode, boolean triggered, int points, String reason) {
        this.ruleCode = ruleCode;
        this.triggered = triggered;
        this.points = points;
        this.reason = reason;
    }

    /** Set by {@code RiskAssessment.record}; the parent owns the relationship. */
    void attachTo(RiskAssessment riskAssessment) {
        this.riskAssessment = riskAssessment;
    }

    /**
     * Overwrites this row's verdict on a re-assessment, keeping its identity.
     *
     * <p>The row is reused rather than replaced so that re-evaluation is an
     * update, not a delete-and-insert — see {@code RiskAssessment.record}.
     */
    void overwriteWith(RiskRuleResult fresh) {
        this.triggered = fresh.triggered;
        this.points = fresh.points;
        this.reason = fresh.reason;
    }
}
