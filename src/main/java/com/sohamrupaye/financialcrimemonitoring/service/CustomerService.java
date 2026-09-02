package com.sohamrupaye.financialcrimemonitoring.service;

import com.sohamrupaye.financialcrimemonitoring.dto.CreateCustomerRequest;
import com.sohamrupaye.financialcrimemonitoring.dto.CustomerResponse;
import com.sohamrupaye.financialcrimemonitoring.exception.DuplicateResourceException;
import com.sohamrupaye.financialcrimemonitoring.exception.ResourceNotFoundException;
import com.sohamrupaye.financialcrimemonitoring.mapper.CustomerMapper;
import com.sohamrupaye.financialcrimemonitoring.model.Customer;
import com.sohamrupaye.financialcrimemonitoring.model.enums.RiskLevel;
import com.sohamrupaye.financialcrimemonitoring.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

/**
 * Business rules for customers. This is where the application's actual decisions
 * live — the controller only translates HTTP, the repository only moves rows.
 *
 * <p>A class rather than an interface plus a single {@code Impl}. Spring proxies
 * classes and Mockito mocks them, so the pair would be two files to read instead
 * of one. Add the interface when a second implementation appears.
 *
 * <p>{@code readOnly = true} at class level makes reads the default; writes
 * override it with a plain {@code @Transactional}.
 */
@Service
@Transactional(readOnly = true)
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private static final String REFERENCE_PREFIX = "CUST-";

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public Page<CustomerResponse> findAll(Pageable pageable) {
        // Page.map keeps the paging metadata intact while converting the content.
        return customerRepository.findAll(pageable).map(CustomerMapper::toResponse);
    }

    public CustomerResponse findByReference(String customerReference) {
        Customer customer = requireByReference(customerReference);
        return CustomerMapper.toResponse(customer);
    }

    public Page<CustomerResponse> findByRiskLevel(RiskLevel riskLevel, Pageable pageable) {
        return customerRepository.findByRiskLevel(riskLevel, pageable)
                .map(CustomerMapper::toResponse);
    }

    /** The duplicate check and the insert share one transaction. */
    @Transactional
    public CustomerResponse create(CreateCustomerRequest request) {
        String email = request.email().toLowerCase(Locale.ROOT);

        // A friendly 409 for the common case. Being check-then-act, the UNIQUE
        // constraint in V1 remains the real guarantee.
        if (customerRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("A customer with email %s already exists".formatted(email));
        }

        Customer customer = new Customer(
                generateReference(),
                request.firstName().trim(),
                request.lastName().trim(),
                email,
                request.dateOfBirth(),
                request.countryCode().toUpperCase(Locale.ROOT),
                // Server-assigned. Everyone starts LOW and is re-rated later.
                RiskLevel.LOW
        );

        Customer saved = customerRepository.save(customer);
        log.info("Created customer {}", saved.getCustomerReference());

        return CustomerMapper.toResponse(saved);
    }

    /**
     * Returns the entity rather than a DTO because callers inside this layer may
     * need to modify it. Package-private so it stays off the public surface.
     */
    Customer requireByReference(String customerReference) {
        return customerRepository.findByCustomerReference(customerReference)
                .orElseThrow(() -> ResourceNotFoundException.of("Customer", customerReference));
    }

    private String generateReference() {
        // Random, not sequential, so references reveal nothing about volume.
        return REFERENCE_PREFIX + UUID.randomUUID().toString()
                .substring(0, 8)
                .toUpperCase(Locale.ROOT);
    }
}
