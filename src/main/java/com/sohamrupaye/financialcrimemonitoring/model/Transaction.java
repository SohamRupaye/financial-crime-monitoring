package com.sohamrupaye.financialcrimemonitoring.model;

import com.sohamrupaye.financialcrimemonitoring.model.enums.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A single movement of money on a monitored account. This is what the rules
 * engine scores.
 */
@Entity
@Table(
        name = "transactions",
        indexes = {
                // The index the velocity and structuring lookbacks live on: both
                // ask for one account's recent activity, newest first.
                @Index(name = "idx_transactions_account_occurred_at",
                        columnList = "account_id, occurred_at DESC"),
                @Index(name = "idx_transactions_occurred_at", columnList = "occurred_at DESC"),
                @Index(name = "idx_transactions_amount", columnList = "amount")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Transaction extends BaseEntity {

    @Column(nullable = false, unique = true, length = 32)
    private String transactionReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransactionType transactionType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    /**
     * Nullable, and a plain string rather than a relationship: the other side of
     * a transfer usually belongs to a different institution, so there is no row
     * of ours to point at.
     */
    @Column(length = 34)
    private String counterpartyAccountNumber;

    @Column(nullable = false, length = 2)
    private String counterpartyCountry;

    /**
     * When the money actually moved, as distinct from the inherited
     * {@code createdAt}, which is when we were told about it. Backfilled and
     * late-arriving data make those two very different instants, and every rule
     * time window is measured on this one.
     */
    @Column(nullable = false)
    private Instant occurredAt;

    public Transaction(String transactionReference,
                       Account account,
                       TransactionType transactionType,
                       BigDecimal amount,
                       String currency,
                       String counterpartyAccountNumber,
                       String counterpartyCountry,
                       Instant occurredAt) {
        this.transactionReference = transactionReference;
        this.account = account;
        this.transactionType = transactionType;
        this.amount = amount;
        this.currency = currency;
        this.counterpartyAccountNumber = counterpartyAccountNumber;
        this.counterpartyCountry = counterpartyCountry;
        this.occurredAt = occurredAt;
    }
}
