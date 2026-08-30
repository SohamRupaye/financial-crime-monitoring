package com.sohamrupaye.financialcrimemonitoring.model;

import com.sohamrupaye.financialcrimemonitoring.model.enums.AlertStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A risk assessment that crossed the alerting threshold and now needs a person.
 */
@Entity
@Table(
        name = "alerts",
        indexes = {
                // Analysts work a queue, and the queue is "everything not yet
                // closed" - so status is the column that gets filtered on.
                @Index(name = "idx_alerts_status", columnList = "status")
        }
)
@Getter
@NoArgsConstructor
public class Alert extends BaseEntity {

    @Column(nullable = false, unique = true, length = 32)
    private String alertReference;

    /**
     * One alert per assessment. Re-scoring a transaction must not stack up a
     * second alert for the same finding.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "risk_assessment_id", nullable = false, unique = true)
    private RiskAssessment riskAssessment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertStatus status;

    public Alert(String alertReference, RiskAssessment riskAssessment) {
        this.alertReference = alertReference;
        this.riskAssessment = riskAssessment;
        // Always OPEN. There is no legitimate way to raise an alert that is
        // already acknowledged, and no caller supplies this.
        this.status = AlertStatus.OPEN;
    }
}
