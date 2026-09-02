package com.sohamrupaye.financialcrimemonitoring.repository;

import com.sohamrupaye.financialcrimemonitoring.model.Customer;
import com.sohamrupaye.financialcrimemonitoring.model.enums.RiskLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Data access for {@link Customer}. */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByCustomerReference(String customerReference);

    /** Cheaper than loading an entity just to discard it. */
    boolean existsByEmail(String email);

    Page<Customer> findByRiskLevel(RiskLevel riskLevel, Pageable pageable);

    /**
     * Written out rather than derived: the method name for this would be ninety
     * characters long.
     */
    @Query("""
            SELECT c FROM Customer c
            WHERE c.countryCode = :countryCode
              AND c.riskLevel IN (
                com.sohamrupaye.financialcrimemonitoring.model.enums.RiskLevel.HIGH,
                com.sohamrupaye.financialcrimemonitoring.model.enums.RiskLevel.CRITICAL
              )
            """)
    Page<Customer> findElevatedRiskInCountry(String countryCode, Pageable pageable);
}
