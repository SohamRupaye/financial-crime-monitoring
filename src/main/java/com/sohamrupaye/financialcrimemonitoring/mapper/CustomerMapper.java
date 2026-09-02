package com.sohamrupaye.financialcrimemonitoring.mapper;

import com.sohamrupaye.financialcrimemonitoring.dto.CustomerResponse;
import com.sohamrupaye.financialcrimemonitoring.model.Customer;

/**
 * Translates entities into response DTOs.
 *
 * <p>Hand-written is fine at this size. MapStruct generates these at compile time
 * once it gets tedious — preferable to a reflection-based mapper, which moves
 * mapping mistakes from compile time to production.
 */
public final class CustomerMapper {

    private CustomerMapper() {
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
