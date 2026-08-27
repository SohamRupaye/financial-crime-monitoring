package com.sohamrupaye.financialcrimemonitoring.rules;

import com.sohamrupaye.financialcrimemonitoring.model.enums.RiskLevel;
import org.springframework.stereotype.Component;

/**
 * Carries the customer's standing rating into the transaction's score.
 *
 * <p>The same transfer means something different depending on who made it. This
 * is the only rule that looks at the customer rather than the movement, and the
 * only one that fires with no history at all.
 */
@Component
public class CustomerRiskRule implements AmlRule {

    @Override
    public RuleCode code() {
        return RuleCode.CUSTOMER_RISK;
    }

    @Override
    public RuleResult evaluate(RuleContext context) {
        RiskLevel riskLevel = context.customer().getRiskLevel();

        // No default branch on purpose. An exhaustive switch over the enum means
        // adding a risk level becomes a compile error here rather than silently
        // scoring zero.
        int points = switch (riskLevel) {
            case LOW -> 0;
            case MEDIUM -> 10;
            case HIGH -> 20;
            case CRITICAL -> 30;
        };

        if (points == 0) {
            return RuleResult.notTriggered(code());
        }

        return RuleResult.triggered(code(), points,
                "Customer risk rating is %s".formatted(riskLevel));
    }
}
