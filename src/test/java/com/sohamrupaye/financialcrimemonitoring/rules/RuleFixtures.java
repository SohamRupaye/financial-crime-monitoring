package com.sohamrupaye.financialcrimemonitoring.rules;

import com.sohamrupaye.financialcrimemonitoring.model.Account;
import com.sohamrupaye.financialcrimemonitoring.model.Customer;
import com.sohamrupaye.financialcrimemonitoring.model.Transaction;
import com.sohamrupaye.financialcrimemonitoring.model.enums.AccountStatus;
import com.sohamrupaye.financialcrimemonitoring.model.enums.AccountType;
import com.sohamrupaye.financialcrimemonitoring.model.enums.RiskLevel;
import com.sohamrupaye.financialcrimemonitoring.model.enums.TransactionType;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Fixtures for the rule tests. No Spring, no database — rules are pure logic and
 * their tests should stay that way.
 */
final class RuleFixtures {

    /** A fixed clock. Rules deal in windows, so "now" has to be reproducible. */
    static final Instant NOW = Instant.parse("2026-09-01T10:00:00Z");

    private RuleFixtures() {
    }

    static Instant minutesAgo(long minutes) {
        return NOW.minus(Duration.ofMinutes(minutes));
    }

    static Customer customer(RiskLevel riskLevel) {
        return new Customer("CUST-3F2A9C41", "Asha", "Menon", "asha.menon@example.com",
                LocalDate.of(1990, 5, 17), "IN", riskLevel);
    }

    static Account account(Customer owner) {
        return new Account("ACC-9B41C7E20D5A", owner, "INR", BigDecimal.ZERO,
                LocalDate.of(2026, 1, 10), AccountType.SAVINGS, AccountStatus.ACTIVE);
    }

    static Transaction transaction(Account on, String amount, Instant occurredAt) {
        return transaction(on, amount, occurredAt, "IN");
    }

    static Transaction transaction(Account on, String amount, Instant occurredAt, String country) {
        return new Transaction("TXN-" + occurredAt.toEpochMilli(), on, TransactionType.TRANSFER,
                new BigDecimal(amount), "INR", "ACC-EXTERNAL-8841", country, occurredAt);
    }

    /**
     * An in-memory {@link TransactionHistory} honouring the documented contract:
     * same account, {@code [occurredAt - window, occurredAt)}, newest first, and
     * never the transaction being evaluated.
     *
     * <p>It filters by window rather than returning a fixed list on purpose. A
     * stub that ignored the window would make every boundary test below pass
     * without proving anything about the rule's own choice of window.
     */
    static TransactionHistory historyOf(Transaction... priorActivity) {
        List<Transaction> all = Arrays.asList(priorActivity);

        return (transaction, window) -> {
            Instant from = transaction.getOccurredAt().minus(window);

            return all.stream()
                    .filter(candidate -> candidate != transaction)
                    .filter(candidate -> !candidate.getOccurredAt().isBefore(from))
                    .filter(candidate -> candidate.getOccurredAt()
                            .isBefore(transaction.getOccurredAt()))
                    .sorted(Comparator.comparing(Transaction::getOccurredAt).reversed())
                    .toList();
        };
    }

    static RuleContext context(Transaction transaction, Customer customer,
                               TransactionHistory history) {
        return new RuleContext(transaction, customer, history);
    }
}
