package com.sohamrupaye.financialcrimemonitoring.repository;

import com.sohamrupaye.financialcrimemonitoring.TestcontainersConfiguration;
import com.sohamrupaye.financialcrimemonitoring.config.JpaAuditingConfig;
import com.sohamrupaye.financialcrimemonitoring.model.Customer;
import com.sohamrupaye.financialcrimemonitoring.model.enums.RiskLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The JPA slice only — entities, repositories, a datasource. Each test runs in a
 * transaction that is rolled back afterwards.
 *
 * <p>{@code replace = NONE} is essential: {@code @DataJpaTest} otherwise swaps in
 * an embedded database, which would quietly test against a different engine than
 * production.
 *
 * <p>{@code JpaAuditingConfig} has to be imported explicitly, because the slice
 * does not load every {@code @Configuration}. Without it the {@code NOT NULL}
 * audit columns stay null and every insert fails.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class CustomerRepositoryTest {

    @Autowired
    private CustomerRepository customerRepository;

    private Customer newCustomer(String reference, String email, String country, RiskLevel risk) {
        return new Customer(reference, "Test", "Customer", email,
                LocalDate.of(1990, 1, 1), country, risk);
    }

    @Test
    @DisplayName("Flyway ran and auditing populates timestamps on save")
    void savePopulatesAuditFields() {
        Customer saved = customerRepository.save(
                newCustomer("CUST-A0000001", "a@example.com", "IN", RiskLevel.LOW));

        // An ID proves BIGSERIAL is wired; timestamps prove auditing is.
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("findByCustomerReference() resolves the derived query correctly")
    void findByCustomerReference() {
        customerRepository.save(
                newCustomer("CUST-B0000002", "b@example.com", "GB", RiskLevel.HIGH));

        Optional<Customer> found = customerRepository.findByCustomerReference("CUST-B0000002");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("b@example.com");
        assertThat(found.get().getRiskLevel()).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    @DisplayName("findByCustomerReference() is empty for an unknown reference")
    void findByCustomerReferenceMissing() {
        assertThat(customerRepository.findByCustomerReference("CUST-MISSING")).isEmpty();
    }

    @Test
    @DisplayName("existsByEmail() is case-sensitive, so the service must normalise")
    void existsByEmailIsCaseSensitive() {
        customerRepository.save(
                newCustomer("CUST-C0000003", "c@example.com", "IN", RiskLevel.LOW));

        assertThat(customerRepository.existsByEmail("c@example.com")).isTrue();
        // Postgres compares text exactly, which is why the service lowercases
        // before checking.
        assertThat(customerRepository.existsByEmail("C@EXAMPLE.COM")).isFalse();
    }

    @Test
    @DisplayName("@Query with an IN clause returns only elevated-risk customers")
    void findElevatedRiskInCountry() {
        customerRepository.save(newCustomer("CUST-D0000004", "d@example.com", "IN", RiskLevel.LOW));
        customerRepository.save(newCustomer("CUST-E0000005", "e@example.com", "IN", RiskLevel.HIGH));
        customerRepository.save(newCustomer("CUST-F0000006", "f@example.com", "IN", RiskLevel.CRITICAL));
        customerRepository.save(newCustomer("CUST-G0000007", "g@example.com", "GB", RiskLevel.CRITICAL));

        Page<Customer> elevated =
                customerRepository.findElevatedRiskInCountry("IN", PageRequest.of(0, 10));

        assertThat(elevated.getTotalElements()).isEqualTo(2);
        assertThat(elevated.getContent())
                .extracting(Customer::getRiskLevel)
                .containsExactlyInAnyOrder(RiskLevel.HIGH, RiskLevel.CRITICAL);
    }
}
