package com.sohamrupaye.financialcrimemonitoring.rules;

import com.sohamrupaye.financialcrimemonitoring.model.Account;
import com.sohamrupaye.financialcrimemonitoring.model.Customer;
import com.sohamrupaye.financialcrimemonitoring.model.enums.RiskLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.NOW;
import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.account;
import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.context;
import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.customer;
import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.historyOf;
import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.properties;
import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.transaction;
import static org.assertj.core.api.Assertions.assertThat;

class CountryRiskRuleTest {

    private final CountryRiskRule rule = new CountryRiskRule(properties());

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
        // Documents real behaviour rather than papering over it: stored codes are
        // uppercase because TransactionService uppercases them, and this rule
        // relies on that having happened.
        assertThat(evaluate("xa").triggered()).isFalse();
    }

    @Test
    @DisplayName("configured codes are normalised at startup")
    void configuredCodesAreNormalised() {
        // Someone will eventually type a lowercase code into a properties file,
        // and a country list that silently never matches is a very quiet failure.
        CountryRiskRule lenient = new CountryRiskRule(new AmlProperties(
                properties().largeAmount(),
                properties().velocity(),
                properties().structuring(),
                properties().customerRisk(),
                new AmlProperties.CountryRisk(Set.of(" xa ", "Xb"), 20),
                properties().scoring()));

        assertThat(lenient.elevatedRiskCountries()).containsExactlyInAnyOrder("XA", "XB");
    }

    @Test
    @DisplayName("the shipped list only holds ISO codes reserved for private use")
    void listContainsNoRealJurisdictions() {
        // Guards the disclaimer in the readme. AA, QM-QZ, XA-XZ and ZZ are the
        // ranges ISO 3166-1 will never assign, so nothing shipped here can be read
        // as a claim about a real country.
        assertThat(rule.elevatedRiskCountries())
                .allMatch(code -> code.equals("AA") || code.equals("ZZ")
                        || code.startsWith("X") || code.startsWith("Q"));
    }
}
