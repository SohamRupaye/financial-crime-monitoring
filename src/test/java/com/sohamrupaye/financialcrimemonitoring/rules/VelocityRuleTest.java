package com.sohamrupaye.financialcrimemonitoring.rules;

import com.sohamrupaye.financialcrimemonitoring.model.Account;
import com.sohamrupaye.financialcrimemonitoring.model.Customer;
import com.sohamrupaye.financialcrimemonitoring.model.Transaction;
import com.sohamrupaye.financialcrimemonitoring.model.enums.RiskLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.NOW;
import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.account;
import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.context;
import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.customer;
import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.historyOf;
import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.properties;
import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.transaction;
import static org.assertj.core.api.Assertions.assertThat;

class VelocityRuleTest {

    private final VelocityRule rule = new VelocityRule(properties());

    private final Customer customer = customer(RiskLevel.LOW);
    private final Account account = account(customer);

    /** {@code count} small transactions, ten seconds apart, all inside the window. */
    private Transaction[] recentActivity(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(index -> transaction(account, "1000", NOW.minusSeconds(index * 10L)))
                .toArray(Transaction[]::new);
    }

    private RuleResult evaluateWith(int priorCount) {
        Transaction current = transaction(account, "1000", NOW);
        return rule.evaluate(context(current, customer, historyOf(recentActivity(priorCount))));
    }

    @Test
    @DisplayName("a burst above the limit triggers and names the count")
    void triggersOnBurst() {
        // Ten prior plus the one being evaluated is eleven, over the limit of ten.
        RuleResult result = evaluateWith(10);

        assertThat(result.triggered()).isTrue();
        assertThat(result.points()).isEqualTo(20);
        assertThat(result.reason())
                .isEqualTo("11 transactions in 10 minutes exceeded the limit of 10");
    }

    @Test
    @DisplayName("ordinary activity does not trigger")
    void doesNotTriggerOnNormalActivity() {
        RuleResult result = evaluateWith(4);

        assertThat(result.triggered()).isFalse();
        assertThat(result.points()).isZero();
    }

    @Test
    @DisplayName("a single transaction on a quiet account does not trigger")
    void doesNotTriggerWithNoHistory() {
        assertThat(evaluateWith(0).triggered()).isFalse();
    }
}
