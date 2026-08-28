package com.sohamrupaye.financialcrimemonitoring.rules;

import com.sohamrupaye.financialcrimemonitoring.model.enums.RiskLevel;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Carries the customer's standing rating into the transaction's score.
 *
 * <p>The same transfer means something different depending on who made it. This
 * is the only rule that looks at the customer rather than the movement, and the
 * only one that fires with no history at all.
 */
@Component
public class CustomerRiskRule implements AmlRule {

    private final Map<RiskLevel, Integer> pointsByRiskLevel;

    /**
     * A configured map costs the exhaustive {@code switch} this used to be, which
     * made a new {@link RiskLevel} a compile error. A missing key would now be a
     * silent zero instead, so the check moves to startup: the map has to cover
     * every level or the application does not come up.
     */
    public CustomerRiskRule(AmlProperties properties) {
        Map<RiskLevel, Integer> configured = new EnumMap<>(properties.customerRisk().points());

        List<RiskLevel> missing = Arrays.stream(RiskLevel.values())
                .filter(level -> !configured.containsKey(level))
                .toList();

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "aml.customer-risk.points is missing an entry for " + missing);
        }

        this.pointsByRiskLevel = Map.copyOf(configured);
    }

    @Override
    public RuleCode code() {
        return RuleCode.CUSTOMER_RISK;
    }

    @Override
    public RuleResult evaluate(RuleContext context) {
        RiskLevel riskLevel = context.customer().getRiskLevel();
        int points = pointsByRiskLevel.get(riskLevel);

        if (points == 0) {
            return RuleResult.notTriggered(code());
        }

        return RuleResult.triggered(code(), points,
                "Customer risk rating is %s".formatted(riskLevel));
    }
}
