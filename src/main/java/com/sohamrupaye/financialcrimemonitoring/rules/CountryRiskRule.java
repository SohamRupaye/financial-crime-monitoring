package com.sohamrupaye.financialcrimemonitoring.rules;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Flags a counterparty in a jurisdiction the institution has chosen to watch.
 *
 * <p><strong>The list below is placeholder data.</strong> Every code in it comes
 * from the ranges ISO 3166-1 reserves for private use — {@code AA},
 * {@code QM}–{@code QZ}, {@code XA}–{@code XZ}, {@code ZZ} — which are
 * guaranteed never to be assigned to a real country. That is deliberate: a demo
 * project has no business shipping a list of real jurisdictions and implying it
 * is a regulatory classification. A deployment would load its own list from
 * configuration, sourced from whatever its compliance function actually
 * publishes.
 */
@Component
public class CountryRiskRule implements AmlRule {

    static final Set<String> ELEVATED_RISK_COUNTRIES = Set.of("XA", "XB", "XC", "QM");

    private static final int POINTS = 20;

    @Override
    public RuleCode code() {
        return RuleCode.COUNTRY_RISK;
    }

    @Override
    public RuleResult evaluate(RuleContext context) {
        String country = context.transaction().getCounterpartyCountry();

        if (!ELEVATED_RISK_COUNTRIES.contains(country)) {
            return RuleResult.notTriggered(code());
        }

        return RuleResult.triggered(code(), POINTS,
                "Counterparty country %s requires additional scrutiny".formatted(country));
    }
}
