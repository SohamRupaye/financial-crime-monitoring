package com.sohamrupaye.financialcrimemonitoring.repository;

import com.sohamrupaye.financialcrimemonitoring.model.RiskAssessment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, Long> {

    /**
     * The rule results are a lazy collection and the response always needs them,
     * so they are fetched here rather than one query at a time in the mapper.
     */
    @EntityGraph(attributePaths = {"ruleResults", "transaction"})
    Optional<RiskAssessment> findByTransaction_TransactionReference(String transactionReference);
}
