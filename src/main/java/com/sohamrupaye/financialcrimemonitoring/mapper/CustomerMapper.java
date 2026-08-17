package com.sohamrupaye.financialcrimemonitoring.mapper;

import com.sohamrupaye.financialcrimemonitoring.dto.CustomerResponse;
import com.sohamrupaye.financialcrimemonitoring.model.Customer;

/**
 * Translates entities into response DTOs.
 *
 * <p>Kept out of both the entity and the service so neither has to know about the
 * other's shape. A utility class rather than a Spring bean: it holds no state and
 * has no dependencies, so there is nothing to inject and nothing to mock.
 *
 * <p>Hand-written mapping is fine at this size. Once there are a dozen entities
 * and the methods become tedious, MapStruct generates them at compile time —
 * prefer it to reflection-based mappers like ModelMapper, which move mapping
 * mistakes from compile time to production.
 */
public final class CustomerMapper {

    private CustomerMapper() {
        // Utility class — never instantiated.
    }

    public static CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.getCustomerReference(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.fullName(),
                customer.getEmail(),
                customer.getDateOfBirth(),
                customer.getCountryCode(),
                customer.getRiskLevel(),
                customer.getCreatedAt()
        );
    }
}
