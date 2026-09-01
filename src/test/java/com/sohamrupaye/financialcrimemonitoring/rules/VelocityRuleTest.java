package com.sohamrupaye.financialcrimemonitoring.rules;

import com.sohamrupaye.financialcrimemonitoring.model.Account;
import com.sohamrupaye.financialcrimemonitoring.model.Customer;
import com.sohamrupaye.financialcrimemonitoring.model.Transaction;

import java.time.Instant;
import com.sohamrupaye.financialcrimemonitoring.model.enums.RiskLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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

    @Nested
    @DisplayName("the count boundary")
    class CountBoundary {

        @Test
        @DisplayName("exactly the limit does not trigger")
        void exactlyAtLimitDoesNotTrigger() {
            // Nine prior plus this one is ten, and the comparison is <=. Off by one
            // here changes whether an alert fires, so both sides are pinned.
            assertThat(evaluateWith(9).triggered()).isFalse();
        }

        @Test
        @DisplayName("one over the limit triggers")
        void oneOverLimitTriggers() {
            assertThat(evaluateWith(10).triggered()).isTrue();
        }
    }

    @Nested
    @DisplayName("the window boundary")
    class WindowBoundary {

        /**
         * Nine filler transactions well inside the window, plus one placed
         * exactly where the caller asks. Ten prior in total, so whether the tenth
         * counts is the difference between triggering and not.
         */
        private RuleResult evaluateWithOneAt(Instant edge) {
            List<Transaction> priorActivity = new ArrayList<>(
                    List.of(recentActivity(9)));
            priorActivity.add(transaction(account, "1000", edge));

            return rule.evaluate(context(transaction(account, "1000", NOW), customer,
                    historyOf(priorActivity.toArray(new Transaction[0]))));
        }

        @Test
        @DisplayName("a transaction exactly on the lower bound is inside the window")
        void lowerBoundIsInclusive() {
            // The window is [occurredAt - 10m, occurredAt), so the oldest instant
            // that still counts is exactly ten minutes back.
            assertThat(evaluateWithOneAt(NOW.minus(Duration.ofMinutes(10))).triggered())
                    .isTrue();
        }

        @Test
        @DisplayName("one millisecond earlier falls outside")
        void justBeforeLowerBoundIsExcluded() {
            assertThat(evaluateWithOneAt(
                    NOW.minus(Duration.ofMinutes(10)).minusMillis(1)).triggered())
                    .isFalse();
        }

        @Test
        @DisplayName("the same volume spread over two windows does not trigger")
        void volumeSpreadAcrossWindowsDoesNotTrigger() {
            // Twenty transactions, none of them within ten minutes of this one.
            // Velocity is about rate, not total.
            Transaction[] spread = IntStream.rangeClosed(1, 20)
                    .mapToObj(index -> transaction(account, "1000",
                            NOW.minus(Duration.ofMinutes(11L + index))))
                    .toArray(Transaction[]::new);

            RuleResult result = rule.evaluate(context(
                    transaction(account, "1000", NOW), customer, historyOf(spread)));

            assertThat(result.triggered()).isFalse();
        }
    }
}
