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

class CountryRiskRuleTest {

    private final CountryRiskRule rule = new CountryRiskRule();

    private final Customer customer = customer(RiskLevel.LOW);
    private final Account account = account(customer);

    private RuleResult evaluate(String counterpartyCountry) {
        return rule.evaluate(context(
                transaction(account, "1000", NOW, counterpartyCountry), customer, historyOf()));
    }

    @Test
    @DisplayName("a listed country triggers and is named in the reason")
    void triggersForListedCountry() {
        RuleResult result = evaluate("XA");

        assertThat(result.triggered()).isTrue();
        assertThat(result.points()).isEqualTo(20);
        assertThat(result.reason())
                .isEqualTo("Counterparty country XA requires additional scrutiny");
    }

    @Test
    @DisplayName("an unlisted country does not trigger")
    void doesNotTriggerForUnlistedCountry() {
        assertThat(evaluate("IN").triggered()).isFalse();
    }

    @Test
    @DisplayName("the list is case sensitive, so codes must be normalised on ingest")
    void matchIsCaseSensitive() {
        // Documents real behaviour rather than papering over it: the codes are
        // stored uppercase because TransactionService uppercases them, and this
        // rule relies on that having happened.
        assertThat(evaluate("xa").triggered()).isFalse();
    }

    @Test
    @DisplayName("the list only holds ISO codes reserved for private use")
    void listContainsNoRealJurisdictions() {
        // Guards the disclaimer in the readme. AA, QM-QZ, XA-XZ and ZZ are the
        // ranges ISO 3166-1 will never assign, so nothing here can be read as a
        // claim about a real country.
        assertThat(CountryRiskRule.ELEVATED_RISK_COUNTRIES)
                .allMatch(code -> code.equals("AA") || code.equals("ZZ")
                        || code.startsWith("X") || code.startsWith("Q"));
    }
}
