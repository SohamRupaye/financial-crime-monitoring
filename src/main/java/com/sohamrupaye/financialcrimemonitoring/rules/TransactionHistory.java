package com.sohamrupaye.financialcrimemonitoring.rules;

import com.sohamrupaye.financialcrimemonitoring.model.Transaction;

import java.time.Duration;
import java.util.List;

/**
 * The only way a rule may reach outside the transaction in front of it.
 *
 * <p>Deliberately one narrow method rather than a repository. Rules that can only
 * ask this question stay pure logic: their tests are plain JUnit against a stub,
 * with no Spring context, no database and no Docker. It also means the rules
 * cannot quietly start issuing their own queries.
 */
public interface TransactionHistory {

    /**
     * Activity on the same account that preceded the given transaction, newest
     * first.
     *
     * <p>The interval is half-open: {@code [occurredAt - window, occurredAt)}.
     * Excluding the upper bound is what keeps the transaction from counting
     * itself. A transaction sharing the exact same instant is also excluded,
     * which is the right call — nothing can be established about which of two
     * simultaneous movements came first.
     */
    List<Transaction> before(Transaction transaction, Duration window);
}
