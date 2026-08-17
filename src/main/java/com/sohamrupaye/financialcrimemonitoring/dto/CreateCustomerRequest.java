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
 * <p>A {@code record} is ideal for a DTO: immutable, and the constructor,
 * accessors, {@code equals}, {@code hashCode} and {@code toString} are generated.
 * No Lombok needed.
 *
 * <p>Note what is absent — {@code id}, {@code customerReference},
 * {@code riskLevel}, {@code createdAt}. A client must not be able to set its own
 * primary key or hand itself a {@code LOW} risk rating. This is the real reason
 * DTOs exist, beyond decoupling: the request type is the write allowlist.
 *
 * <p>The constraints below only run because the controller parameter is annotated
 * {@code @Valid}. Without it they are silently ignored.
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

        // @NotNull, not @NotBlank: @NotBlank only applies to CharSequence.
        @NotNull(message = "date of birth is required")
        @Past(message = "date of birth must be in the past")
        LocalDate dateOfBirth,

        @NotBlank(message = "country code is required")
        @Pattern(regexp = "^[A-Z]{2}$", message = "country code must be ISO 3166-1 alpha-2, e.g. IN")
        String countryCode
) {
}
