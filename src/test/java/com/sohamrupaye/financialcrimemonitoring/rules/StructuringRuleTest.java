package com.sohamrupaye.financialcrimemonitoring.rules;

import com.sohamrupaye.financialcrimemonitoring.model.Account;
import com.sohamrupaye.financialcrimemonitoring.model.Customer;
import com.sohamrupaye.financialcrimemonitoring.model.Transaction;

import java.time.Instant;
import com.sohamrupaye.financialcrimemonitoring.model.enums.RiskLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.NOW;
import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.account;
import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.context;
import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.customer;
import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.historyOf;
import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.properties;
import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.minutesAgo;
import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.transaction;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class StructuringRuleTest {

    private final StructuringRule rule = new StructuringRule(properties());

    private final Customer customer = customer(RiskLevel.LOW);
    private final Account account = account(customer);

    private RuleResult evaluate(String currentAmount, Transaction... priorActivity) {
        Transaction current = transaction(account, currentAmount, NOW);
        return rule.evaluate(context(current, customer, historyOf(priorActivity)));
    }

    @Test
    @DisplayName("four near-threshold transfers in a day trigger with the total")
    void triggersOnSplitTransfers() {
        // 490,000 x2 + 480,000 + 495,000 = 1,955,000 moved without one
        // transaction ever crossing the 500,000 line.
        RuleResult result = evaluate("495000",
                transaction(account, "490000", minutesAgo(30)),
                transaction(account, "490000", minutesAgo(20)),
                transaction(account, "480000", minutesAgo(10)));

        assertThat(result.triggered()).isTrue();
        assertThat(result.code()).isEqualTo(RuleCode.STRUCTURING);
        assertThat(result.points()).isEqualTo(30);
        assertThat(result.reason()).isEqualTo(
                "4 transactions totalling 1955000 INR in 24 hours, "
                        + "each individually below the 500000 threshold");
    }

    @Test
    @DisplayName("one transaction over the threshold is not structuring")
    void doesNotTriggerOnSingleLargeTransaction() {
        // Nothing was avoided, and LargeAmountRule already has this one.
        assertThat(evaluate("600000").triggered()).isFalse();
    }

    @Test
    @DisplayName("everyday amounts do not add up to structuring")
    void doesNotTriggerOnSmallAmounts() {
        // Well under the floor, so they never look deliberate however many
        // there are.
        RuleResult result = evaluate("4500",
                transaction(account, "2000", minutesAgo(30)),
                transaction(account, "8200", minutesAgo(20)),
                transaction(account, "3700", minutesAgo(10)));

        assertThat(result.triggered()).isFalse();
    }

    @Test
    @DisplayName("a quiet account with one near-threshold transfer does not trigger")
    void doesNotTriggerWithoutEnoughTransactions() {
        assertThat(evaluate("490000").triggered()).isFalse();
    }

    @Nested
    @DisplayName("which amounts count as near-threshold")
    class NearThresholdBounds {

        /** Two qualifying priors, so one more decides whether the rule fires. */
        private RuleResult withTwoQualifyingPriors(String currentAmount) {
            return evaluate(currentAmount,
                    transaction(account, "490000", minutesAgo(30)),
                    transaction(account, "490000", minutesAgo(20)));
        }

        @Test
        @DisplayName("an amount exactly on the floor qualifies")
        void floorIsInclusive() {
            // 400,000 is the floor. Sitting exactly on it was still a choice.
            assertThat(withTwoQualifyingPriors("400000").triggered()).isTrue();
        }

        @Test
        @DisplayName("a cent below the floor does not qualify")
        void justBelowFloorDoesNotQualify() {
            assertThat(withTwoQualifyingPriors("399999.99").triggered()).isFalse();
        }

        @Test
        @DisplayName("an amount exactly on the large-amount threshold still qualifies")
        void thresholdIsInclusive() {
            // Sitting exactly at the reporting line is the most deliberate place
            // to sit, so it counts as structuring rather than falling between the
            // two rules.
            assertThat(withTwoQualifyingPriors("500000").triggered()).isTrue();
        }

        @Test
        @DisplayName("a cent above the threshold is the large amount rule's problem")
        void justAboveThresholdIsNotStructuring() {
            // Nothing was avoided, so this is not structuring - and LargeAmountRule
            // fires on it instead.
            assertThat(withTwoQualifyingPriors("500000.01").triggered()).isFalse();
        }

        @Test
        @DisplayName("amounts outside the band are ignored however many there are")
        void nonQualifyingAmountsAreIgnored() {
            // Twenty small payments and one large one, none in the band. Only the
            // current transaction qualifies, so the count never reaches three.
            RuleResult result = evaluate("490000",
                    transaction(account, "2000", minutesAgo(60)),
                    transaction(account, "3700", minutesAgo(50)),
                    transaction(account, "8200", minutesAgo(40)),
                    transaction(account, "900000", minutesAgo(30)));

            assertThat(result.triggered()).isFalse();
        }
    }

    @Nested
    @DisplayName("how many transactions make a pattern")
    class CountBoundary {

        @Test
        @DisplayName("two transactions over the threshold between them are not a pattern")
        void twoIsNotAPattern() {
            // 980,000 across two transfers clears the threshold, but two large
            // transfers in a day is ordinary business.
            RuleResult result = evaluate("490000",
                    transaction(account, "490000", minutesAgo(30)));

            assertThat(result.triggered()).isFalse();
        }

        @Test
        @DisplayName("three is")
        void threeIsAPattern() {
            RuleResult result = evaluate("490000",
                    transaction(account, "490000", minutesAgo(30)),
                    transaction(account, "490000", minutesAgo(20)));

            assertThat(result.triggered()).isTrue();
            assertThat(result.reason()).contains("3 transactions totalling 1470000");
        }
    }

    @Nested
    @DisplayName("whether the total actually evaded anything")
    class TotalBoundary {

        /**
         * A floor far below the threshold, which the shipped configuration does
         * not have.
         *
         * <p>With the real numbers - floor 400,000, threshold 500,000 - three
         * qualifying amounts always sum past the threshold, so the total check can
         * never be the thing that stops the rule. It stops being unreachable as
         * soon as someone widens the band, which is exactly the kind of change
         * that gets made while tuning, so it is worth having pinned.
         */
        private final StructuringRule wideBand =
                new StructuringRule(RuleFixtures.withAmounts("500000", "100000", 3));

        private RuleResult evaluateWideBand(String currentAmount, String... priorAmounts) {
            Transaction[] priorActivity = new Transaction[priorAmounts.length];
            for (int index = 0; index < priorAmounts.length; index++) {
                priorActivity[index] =
                        transaction(account, priorAmounts[index], minutesAgo(30L - index));
            }

            return wideBand.evaluate(context(
                    transaction(account, currentAmount, NOW), customer, historyOf(priorActivity)));
        }

        @Test
        @DisplayName("three in-band transactions summing under the threshold do not trigger")
        void totalBelowThresholdDoesNotTrigger() {
            // 450,000 across three transfers. Nothing was evaded, because the sum
            // would not have been reportable either.
            assertThat(evaluateWideBand("150000", "150000", "150000").triggered()).isFalse();
        }

        @Test
        @DisplayName("a total exactly on the threshold does not trigger")
        void totalExactlyAtThresholdDoesNotTrigger() {
            // Exactly 500,000. The comparison is strictly greater, matching the
            // large amount rule, so the two agree about where the line is.
            assertThat(evaluateWideBand("200000", "150000", "150000").triggered()).isFalse();
        }

        @Test
        @DisplayName("a cent over the threshold triggers")
        void totalJustOverThresholdTriggers() {
            assertThat(evaluateWideBand("200000.01", "150000", "150000").triggered()).isTrue();
        }
    }

    @Nested
    @DisplayName("the window boundary")
    class WindowBoundary {

        private RuleResult withOldestAt(Instant edge) {
            return evaluate("490000",
                    transaction(account, "490000", minutesAgo(10)),
                    transaction(account, "490000", edge));
        }

        @Test
        @DisplayName("a transaction exactly 24 hours back is inside the window")
        void lowerBoundIsInclusive() {
            assertThat(withOldestAt(NOW.minus(Duration.ofHours(24))).triggered()).isTrue();
        }

        @Test
        @DisplayName("one millisecond earlier falls outside, leaving only two")
        void justBeforeLowerBoundIsExcluded() {
            assertThat(withOldestAt(
                    NOW.minus(Duration.ofHours(24)).minusMillis(1)).triggered())
                    .isFalse();
        }
    }
}
