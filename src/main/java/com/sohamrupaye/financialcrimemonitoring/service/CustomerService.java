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
 * <p>Deliberately a class, not a {@code CustomerService} interface plus a
 * {@code CustomerServiceImpl}. That pair is everywhere in older Spring codebases
 * because pre-5.0 Spring needed an interface for proxying, and because mocking
 * frameworks once required one. Neither is true now: Spring proxies classes via
 * CGLIB and Mockito mocks them fine. An interface with exactly one implementation
 * is two files to read instead of one. Add the interface when a second
 * implementation genuinely appears.
 *
 * <p>{@code @Transactional(readOnly = true)} at class level makes reads the
 * default — it lets the driver skip dirty-checking and flushing, and documents
 * intent. Methods that write override it with a plain {@code @Transactional}.
 */
@Service
@Transactional(readOnly = true)
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private static final String REFERENCE_PREFIX = "CUST-";

    /**
     * Constructor injection, and the field is {@code final}.
     *
     * <p>Prefer this to {@code @Autowired} on a field, which cannot be final, hides
     * dependencies from anyone constructing the class in a test, and lets a class
     * quietly accumulate eight collaborators without the constructor getting
     * embarrassing enough to notice. With a single constructor, the
     * {@code @Autowired} annotation itself is optional and omitted here.
     */
    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public Page<CustomerResponse> findAll(Pageable pageable) {
        // Page.map keeps the paging metadata (total, page number) intact while
        // converting the content. Never return Page<Customer> from a service:
        // that would leak entities to the controller.
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

    /**
     * Creates a customer.
     *
     * <p>{@code @Transactional} without {@code readOnly} opens a read-write
     * transaction spanning the whole method, so the duplicate check and the insert
     * either both happen or neither does.
     */
    @Transactional
    public CustomerResponse create(CreateCustomerRequest request) {
        String email = request.email().toLowerCase(Locale.ROOT);

        // A friendly 409 for the common case. This is a check-then-act race, so
        // the UNIQUE constraint in V1__create_customers_table.sql remains the
        // real guarantee — application checks alone cannot enforce uniqueness
        // under concurrency.
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
                // Server-assigned, never taken from the request. Every new customer
                // starts LOW and is re-rated once transactions are assessed.
                RiskLevel.LOW
        );

        Customer saved = customerRepository.save(customer);
        log.info("Created customer {}", saved.getCustomerReference());

        return CustomerMapper.toResponse(saved);
    }

    /**
     * Shared lookup that throws instead of returning empty.
     *
     * <p>Returns the entity rather than a DTO because callers inside this layer
     * may need to modify it. Package-private: it must not become part of the
     * service's public surface.
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
