package com.sohamrupaye.financialcrimemonitoring.rules;

import com.sohamrupaye.financialcrimemonitoring.model.Account;
import com.sohamrupaye.financialcrimemonitoring.model.Customer;
import com.sohamrupaye.financialcrimemonitoring.model.enums.RiskLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.NOW;
import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.account;
import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.context;
import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.customer;
import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.historyOf;
import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.transaction;
import static org.assertj.core.api.Assertions.assertThat;

class LargeAmountRuleTest {

    private final LargeAmountRule rule = new LargeAmountRule();

    private final Customer customer = customer(RiskLevel.LOW);
    private final Account account = account(customer);

    private RuleResult evaluate(String amount) {
        return rule.evaluate(context(transaction(account, amount, NOW), customer, historyOf()));
    }

    @Test
    @DisplayName("an amount above the threshold triggers with its points and reason")
    void triggersAboveThreshold() {
        RuleResult result = evaluate("500000.01");

        assertThat(result.triggered()).isTrue();
        assertThat(result.code()).isEqualTo(RuleCode.LARGE_AMOUNT);
        assertThat(result.points()).isEqualTo(25);
        assertThat(result.reason()).isEqualTo("Amount 500000.01 INR exceeded the 500000 threshold");
    }

    @Test
    @DisplayName("an amount exactly on the threshold does not trigger")
    void doesNotTriggerExactlyAtThreshold() {
        // The comparison is strictly greater than. Whichever way this goes it has
        // to be a decision, not an accident, so it gets its own test.
        assertThat(evaluate("500000").triggered()).isFalse();
    }

    @Test
    @DisplayName("trailing zeros do not change the comparison")
    void scaleDoesNotAffectComparison() {
        // 500000.0000 equals 500000 by compareTo and differs by equals. Using
        // equals here is the classic money bug, so both spellings are pinned.
        assertThat(evaluate("500000.0000").triggered()).isFalse();
        assertThat(evaluate("500000.0001").triggered()).isTrue();
    }

    @Test
    @DisplayName("an ordinary amount does not trigger and carries no points")
    void doesNotTriggerBelowThreshold() {
        RuleResult result = evaluate("4500");

        assertThat(result.triggered()).isFalse();
        assertThat(result.points()).isZero();
        assertThat(result.reason()).isNull();
    }
}
