package com.sohamrupaye.financialcrimemonitoring.repository;

import com.sohamrupaye.financialcrimemonitoring.model.Alert;
import com.sohamrupaye.financialcrimemonitoring.model.enums.AlertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    /**
     * The graph reaches all the way to the customer because an alert is never
     * useful on its own — an analyst opening one immediately wants to know which
     * transaction and whose account.
     */
    @EntityGraph(attributePaths = {
            "riskAssessment",
            "riskAssessment.ruleResults",
            "riskAssessment.transaction",
            "riskAssessment.transaction.account",
            "riskAssessment.transaction.account.customer"})
    Optional<Alert> findByAlertReference(String alertReference);

    boolean existsByRiskAssessmentId(Long riskAssessmentId);

    @EntityGraph(attributePaths = {
            "riskAssessment",
            "riskAssessment.transaction",
            "riskAssessment.transaction.account",
            "riskAssessment.transaction.account.customer"})
    Page<Alert> findByStatus(AlertStatus status, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {
            "riskAssessment",
            "riskAssessment.transaction",
            "riskAssessment.transaction.account",
            "riskAssessment.transaction.account.customer"})
    Page<Alert> findAll(Pageable pageable);
}
