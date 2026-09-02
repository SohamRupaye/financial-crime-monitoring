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
@NoArgsConstructor
public class Customer extends BaseEntity {

    /**
     * Business key used in URLs, e.g. {@code CUST-3F2A9C41}. Kept separate from
     * the primary key: {@code /customers/1} can be walked upward to enumerate
     * every customer, and leaks how many there are.
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
     * {@code EnumType.STRING}, not the {@code ORDINAL} default — an ordinal
     * silently corrupts every existing row if a constant is inserted mid-list.
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
