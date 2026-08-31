package com.sohamrupaye.financialcrimemonitoring.model;

import com.sohamrupaye.financialcrimemonitoring.exception.IllegalStatusTransitionException;
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

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

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

    /**
     * The workflow, as data.
     *
     * <p>Declared here rather than in a service because it is an invariant of the
     * alert itself: there should be no way to reach an {@code Alert} and move it
     * somewhere illegal.
     *
     * <p>{@code FALSE_POSITIVE} is reachable from every open state. An analyst who
     * can see at a glance that the rules were wrong should not have to walk an
     * alert through acknowledgement and investigation to say so. {@code RESOLVED}
     * is not: resolving means someone looked, and the workflow should reflect
     * that they did.
     */
    private static final Map<AlertStatus, Set<AlertStatus>> ALLOWED_TRANSITIONS =
            new EnumMap<>(Map.of(
                    AlertStatus.OPEN,
                    Set.of(AlertStatus.ACKNOWLEDGED, AlertStatus.FALSE_POSITIVE),

                    AlertStatus.ACKNOWLEDGED,
                    Set.of(AlertStatus.INVESTIGATING, AlertStatus.FALSE_POSITIVE),

                    AlertStatus.INVESTIGATING,
                    Set.of(AlertStatus.RESOLVED, AlertStatus.FALSE_POSITIVE),

                    // Terminal. Reopening is a decision with an audit trail
                    // attached, not a status flip.
                    AlertStatus.RESOLVED, Set.of(),
                    AlertStatus.FALSE_POSITIVE, Set.of()));

    public Alert(String alertReference, RiskAssessment riskAssessment) {
        this.alertReference = alertReference;
        this.riskAssessment = riskAssessment;
        // Always OPEN. There is no legitimate way to raise an alert that is
        // already acknowledged, and no caller supplies this.
        this.status = AlertStatus.OPEN;
    }

    /**
     * Moves the alert on, or refuses.
     *
     * <p>Transitioning to the current status is refused too. A repeated PATCH is
     * more likely to be a client that has lost track of the state than a
     * deliberate no-op, and saying so is more useful than silently agreeing.
     */
    public void transitionTo(AlertStatus target) {
        if (!ALLOWED_TRANSITIONS.get(status).contains(target)) {
            throw new IllegalStatusTransitionException(
                    "Alert %s cannot move from %s to %s. Allowed: %s".formatted(
                            alertReference, status, target,
                            ALLOWED_TRANSITIONS.get(status).isEmpty()
                                    ? "none, " + status + " is final"
                                    : ALLOWED_TRANSITIONS.get(status)));
        }

        this.status = target;
    }
}
