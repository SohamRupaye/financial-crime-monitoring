package com.sohamrupaye.financialcrimemonitoring.rules;

import com.sohamrupaye.financialcrimemonitoring.model.Account;
import com.sohamrupaye.financialcrimemonitoring.model.Customer;
import com.sohamrupaye.financialcrimemonitoring.model.Transaction;
import com.sohamrupaye.financialcrimemonitoring.model.enums.RiskLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.NOW;
import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.account;
import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.context;
import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.customer;
import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.historyOf;
import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.minutesAgo;
import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.transaction;
import static org.assertj.core.api.Assertions.assertThat;

class StructuringRuleTest {

    private final StructuringRule rule = new StructuringRule();

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
}
