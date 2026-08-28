package com.sohamrupaye.financialcrimemonitoring.rules;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Flags a counterparty in a jurisdiction the institution has chosen to watch.
 *
 * <p><strong>The shipped list is placeholder data.</strong> Every code in
 * {@code application.properties} comes from the ranges ISO 3166-1 reserves for
 * private use — {@code AA}, {@code QM}–{@code QZ}, {@code XA}–{@code XZ},
 * {@code ZZ} — which are guaranteed never to be assigned to a real country. That
 * is deliberate: a demo project has no business shipping a list of real
 * jurisdictions and implying it is a regulatory classification. A deployment
 * supplies its own, from whatever its compliance function publishes.
 */
@Component
public class CountryRiskRule implements AmlRule {

    private final Set<String> elevatedRiskCountries;
    private final int points;

    public CountryRiskRule(AmlProperties properties) {
        // Normalised once at startup rather than per transaction. Configuration
        // arrives however someone typed it, and a lowercase entry that silently
        // never matched would be a very quiet failure.
        this.elevatedRiskCountries = properties.countryRisk().elevatedRiskCountries().stream()
                .map(code -> code.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());

        this.points = properties.countryRisk().points();
    }

    @Override
    public RuleCode code() {
        return RuleCode.COUNTRY_RISK;
    }

    @Override
    public RuleResult evaluate(RuleContext context) {
        String country = context.transaction().getCounterpartyCountry();

        if (!elevatedRiskCountries.contains(country)) {
            return RuleResult.notTriggered(code());
        }

        return RuleResult.triggered(code(), points,
                "Counterparty country %s requires additional scrutiny".formatted(country));
    }

    /** Exposed for the test that pins the placeholder-only guarantee. */
    Set<String> elevatedRiskCountries() {
        return elevatedRiskCountries;
    }
}
