package com.sohamrupaye.financialcrimemonitoring.repository;

import com.sohamrupaye.financialcrimemonitoring.model.Customer;
import com.sohamrupaye.financialcrimemonitoring.model.enums.RiskLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Data access for {@link Customer}.
 *
 * <p>This is an interface with no implementation, and that is the whole point.
 * Spring Data generates the implementing bean at startup by parsing the method
 * names. {@code JpaRepository<Customer, Long>} already provides {@code save},
 * {@code findById}, {@code findAll(Pageable)}, {@code delete} and friends, so
 * only the queries specific to this project are declared below.
 *
 * <p>{@code @Repository} is optional here — Spring Data registers the bean
 * regardless — but it documents the role and keeps the layer greppable.
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /**
     * Derived query. Spring Data reads the name: {@code findBy} + property
     * {@code CustomerReference}, and writes the JPQL for you. Misspell the
     * property and the application fails at startup, not at request time.
     *
     * <p>{@code Optional} rather than {@code null}, so callers cannot forget the
     * absent case.
     */
    Optional<Customer> findByCustomerReference(String customerReference);

    /**
     * {@code existsBy} issues a cheap {@code SELECT 1 ... LIMIT 1} instead of
     * loading a whole entity just to discard it.
     */
    boolean existsByEmail(String email);

    Page<Customer> findByRiskLevel(RiskLevel riskLevel, Pageable pageable);

    /**
     * Once a query outgrows a readable method name, write it explicitly rather
     * than building a 90-character method name. The derived-query equivalent
     * would be {@code findByRiskLevelInAndCountryCodeOrderBy...}.
     *
     * <p>{@code :countryCode} is a bound parameter, never string concatenation —
     * that is how SQL injection gets in.
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
