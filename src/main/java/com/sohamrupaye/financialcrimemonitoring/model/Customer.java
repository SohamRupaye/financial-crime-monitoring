package com.sohamrupaye.financialcrimemonitoring.model;

import com.sohamrupaye.financialcrimemonitoring.model.enums.RiskLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * A person the institution monitors. Root of the AML object graph:
 * customer → account → transaction → risk assessment → alert → case.
 *
 * <p>An entity is a database row, not an API contract. Nothing here is ever
 * returned from a controller directly — see {@code CustomerResponse} for why.
 *
 * <p>Note {@code @Table(name = "customers")}. Spring Boot's naming strategy
 * turns {@code Customer} into {@code customer} and {@code dateOfBirth} into
 * {@code date_of_birth}, so the columns need no annotations but the plural
 * table name does.
 */
@Entity
@Table(
        name = "customers",
        indexes = {
                @Index(name = "idx_customers_risk_level", columnList = "risk_level"),
                @Index(name = "idx_customers_country_code", columnList = "country_code")
        }
)
@Getter
@Setter
@NoArgsConstructor // JPA requires a no-arg constructor to hydrate rows.
public class Customer extends BaseEntity {

    /**
     * Business key shown to clients and used in URLs, e.g. {@code CUST-3F2A9C41}.
     *
     * <p>Kept separate from the numeric primary key deliberately: exposing
     * {@code /customers/1} lets anyone walk your entire customer base by counting
     * upward, and it leaks how many customers you have.
     */
    @Column(nullable = false, unique = true, length = 32)
    private String customerReference;

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private LocalDate dateOfBirth;

    @Column(nullable = false, length = 2)
    private String countryCode;

    /**
     * Current standing risk rating, recalculated as transactions are assessed.
     *
     * <p>{@code EnumType.STRING} stores {@code "HIGH"}. The default,
     * {@code ORDINAL}, would store {@code 2} — and silently corrupt every
     * existing row if a new constant were ever inserted mid-list.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RiskLevel riskLevel;

    public Customer(String customerReference,
                    String firstName,
                    String lastName,
                    String email,
                    LocalDate dateOfBirth,
                    String countryCode,
                    RiskLevel riskLevel) {
        this.customerReference = customerReference;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.dateOfBirth = dateOfBirth;
        this.countryCode = countryCode;
        this.riskLevel = riskLevel;
    }

    public String fullName() {
        return firstName + " " + lastName;
    }
}
