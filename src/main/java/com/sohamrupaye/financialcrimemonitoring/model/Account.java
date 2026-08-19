package com.sohamrupaye.financialcrimemonitoring.model;

import com.sohamrupaye.financialcrimemonitoring.model.enums.AccountStatus;
import com.sohamrupaye.financialcrimemonitoring.model.enums.AccountType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
        name="accounts",
        indexes = {
                @Index(name = "idx_accounts_customer_id", columnList = "customer_id")
        }

)
@Getter
@Setter
@NoArgsConstructor
public class Account extends BaseEntity {
        @Column(nullable = false, unique = true, length = 34)
        private String accountNumber;

        @ManyToOne(fetch = FetchType.LAZY, optional=false)
        @JoinColumn(name="customer_id", nullable = false)
        private Customer customer;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 20)
        private AccountType accountType;

        @Column(nullable = false, length = 3)
        private String currency;

        @Column(nullable = false, precision=19, scale=4)
        private BigDecimal balance;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 20)
        private AccountStatus status;

        @Column(nullable = false)
        private LocalDate openedAt;

        public Account(
                String accountNumber,
                Customer customer,
                String currency,
                BigDecimal balance,
                LocalDate openedAt,
                AccountType accountType,
                AccountStatus status
        ){
                this.accountNumber = accountNumber;
                this.customer = customer;
                this.currency = currency;
                this.balance = balance;
                this.openedAt = openedAt;
                this.accountType = accountType;
                this.status = status;
        }
}
