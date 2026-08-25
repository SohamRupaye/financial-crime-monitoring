package com.sohamrupaye.financialcrimemonitoring.rules;

import com.sohamrupaye.financialcrimemonitoring.model.Customer;
import com.sohamrupaye.financialcrimemonitoring.model.Transaction;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Everything a rule is allowed to see.
 *
 * <p>The customer is resolved once by the engine rather than each rule walking
 * {@code transaction.getAccount().getCustomer()} for itself, which would be two
 * lazy loads per rule.
 */
public record RuleContext(Transaction transaction, Customer customer, TransactionHistory history) {

    public RuleContext {
        Objects.requireNonNull(transaction, "transaction is required");
        Objects.requireNonNull(customer, "customer is required");
        Objects.requireNonNull(history, "history is required");
    }

    /** Shorthand so rules read as intent rather than plumbing. */
    public List<Transaction> precedingWindow(Duration window) {
        return history.before(transaction, window);
    }
}
