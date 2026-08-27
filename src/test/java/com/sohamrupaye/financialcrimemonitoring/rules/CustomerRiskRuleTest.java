package com.sohamrupaye.financialcrimemonitoring.rules;

import com.sohamrupaye.financialcrimemonitoring.model.Customer;
import com.sohamrupaye.financialcrimemonitoring.model.enums.RiskLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.NOW;
import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.account;
import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.context;
import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.customer;
import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.historyOf;
import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.transaction;
import static org.assertj.core.api.Assertions.assertThat;

class CustomerRiskRuleTest {

    private final CustomerRiskRule rule = new CustomerRiskRule();

    private RuleResult evaluate(RiskLevel riskLevel) {
        Customer customer = customer(riskLevel);
        return rule.evaluate(context(
                transaction(account(customer), "1000", NOW), customer, historyOf()));
    }

    @ParameterizedTest
    @CsvSource({"MEDIUM,10", "HIGH,20", "CRITICAL,30"})
    @DisplayName("each elevated rating contributes its own points")
    void scoresByRiskLevel(RiskLevel riskLevel, int expectedPoints) {
        RuleResult result = evaluate(riskLevel);

        assertThat(result.triggered()).isTrue();
        assertThat(result.points()).isEqualTo(expectedPoints);
        assertThat(result.reason()).isEqualTo("Customer risk rating is " + riskLevel);
    }

    @Test
    @DisplayName("a LOW rating does not trigger at all")
    void lowRiskDoesNotTrigger() {
        // Zero points would be a triggered rule with nothing to say, so the rule
        // reports itself as quiet instead.
        RuleResult result = evaluate(RiskLevel.LOW);

        assertThat(result.triggered()).isFalse();
        assertThat(result.points()).isZero();
        assertThat(result.reason()).isNull();
    }
}
