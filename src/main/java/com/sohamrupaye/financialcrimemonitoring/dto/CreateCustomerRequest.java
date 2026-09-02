package com.sohamrupaye.financialcrimemonitoring.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * What a client is allowed to send when creating a customer.
 *
 * <p>Note what is absent — no {@code id}, no {@code customerReference}, no
 * {@code riskLevel}. A client must not set its own key or hand itself a risk
 * rating. The request type is the write allowlist.
 */
public record CreateCustomerRequest(

        @NotBlank(message = "first name is required")
        @Size(max = 100, message = "first name must be at most 100 characters")
        String firstName,

        @NotBlank(message = "last name is required")
        @Size(max = 100, message = "last name must be at most 100 characters")
        String lastName,

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid address")
        @Size(max = 255)
        String email,

        @NotNull(message = "date of birth is required")
        @Past(message = "date of birth must be in the past")
        LocalDate dateOfBirth,

        @NotBlank(message = "country code is required")
        @Pattern(regexp = "^[A-Z]{2}$", message = "country code must be ISO 3166-1 alpha-2, e.g. IN")
        String countryCode
) {
}
