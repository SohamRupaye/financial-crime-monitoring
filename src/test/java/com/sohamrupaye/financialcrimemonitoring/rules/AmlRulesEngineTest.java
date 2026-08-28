package com.sohamrupaye.financialcrimemonitoring.rules;

import com.sohamrupaye.financialcrimemonitoring.model.Account;
import com.sohamrupaye.financialcrimemonitoring.model.Customer;
import com.sohamrupaye.financialcrimemonitoring.model.Transaction;
import com.sohamrupaye.financialcrimemonitoring.model.enums.RiskLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.NOW;
import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.account;
import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.customer;
import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.historyOf;
import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.properties;
import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.transaction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AmlRulesEngineTest {

    private final Customer customer = customer(RiskLevel.HIGH);
    private final Account account = account(customer);
    private final Transaction transaction = transaction(account, "600000", NOW);

    /** A rule that always fires, so ordering and collection can be checked. */
    private static AmlRule alwaysFiring(RuleCode code) {
        return new AmlRule() {
            @Override
            public RuleCode code() {
                return code;
            }

            @Override
            public RuleResult evaluate(RuleContext context) {
                return RuleResult.triggered(code, 10, code + " fired");
            }
        };
    }

    private AmlRulesEngine engineOf(AmlRule... rules) {
        return new AmlRulesEngine(List.of(rules), historyOf());
    }

    @Test
    @DisplayName("every rule contributes exactly one result")
    void collectsOneResultPerRule() {
        AmlRulesEngine engine = engineOf(
                alwaysFiring(RuleCode.VELOCITY), new LargeAmountRule(properties()));

        List<RuleResult> results = engine.evaluate(transaction);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(RuleResult::triggered);
    }

    @Test
    @DisplayName("results are ordered by rule code, not by bean order")
    void resultsAreOrderedByCode() {
        // Registered deliberately out of order. Spring does not guarantee the
        // order of an injected List, so the engine sorts - otherwise the reasons
        // on an assessment would shuffle between runs.
        AmlRulesEngine engine = engineOf(
                alwaysFiring(RuleCode.COUNTRY_RISK),
                alwaysFiring(RuleCode.CUSTOMER_RISK),
                new LargeAmountRule(properties()));

        assertThat(engine.evaluate(transaction))
                .extracting(RuleResult::code)
                .containsExactly(RuleCode.LARGE_AMOUNT, RuleCode.CUSTOMER_RISK,
                        RuleCode.COUNTRY_RISK);
    }

    @Test
    @DisplayName("untriggered rules are still reported")
    void reportsUntriggeredRules() {
        AmlRulesEngine engine = engineOf(new LargeAmountRule(properties()));

        // Small amount, so the only rule stays quiet. The result still comes back,
        // which is what later makes "why was there no alert" answerable.
        List<RuleResult> results = engine.evaluate(transaction(account, "4500", NOW));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).triggered()).isFalse();
    }

    @Test
    @DisplayName("the customer comes from the transaction's own account")
    void resolvesCustomerFromAccount() {
        AmlRule capturing = new AmlRule() {
            @Override
            public RuleCode code() {
                return RuleCode.CUSTOMER_RISK;
            }

            @Override
            public RuleResult evaluate(RuleContext context) {
                assertThat(context.customer()).isSameAs(customer);
                assertThat(context.transaction()).isSameAs(transaction);
                return RuleResult.notTriggered(code());
            }
        };

        engineOf(capturing).evaluate(transaction);
    }

    @Test
    @DisplayName("starting with no rules fails loudly instead of scoring everything zero")
    void refusesToStartWithNoRules() {
        assertThatThrownBy(() -> new AmlRulesEngine(List.of(), historyOf()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no AML rules");
    }

    @Test
    @DisplayName("two rules sharing a code fail at startup")
    void refusesDuplicateRuleCodes() {
        // Left alone this would double-count one rule and make the stored results
        // ambiguous, with nothing to indicate it at request time.
        assertThatThrownBy(() -> engineOf(
                alwaysFiring(RuleCode.VELOCITY), alwaysFiring(RuleCode.VELOCITY)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate rule code");
    }
}
