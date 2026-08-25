package com.sohamrupaye.financialcrimemonitoring.rules;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Flags a single transaction that is large on its own.
 *
 * <p>The simplest rule in the set, and the one the structuring rule is built to
 * defeat — anyone splitting a transfer is specifically trying to stay under this
 * threshold.
 */
@Component
public class LargeAmountRule implements AmlRule {

    static final BigDecimal THRESHOLD = new BigDecimal("500000");

    private static final int POINTS = 25;

    @Override
    public RuleCode code() {
        return RuleCode.LARGE_AMOUNT;
    }

    @Override
    public RuleResult evaluate(RuleContext context) {
        BigDecimal amount = context.transaction().getAmount();

        // compareTo, never equals: 500000 and 500000.0000 are the same amount and
        // different objects. Strictly greater, so an amount sitting exactly on the
        // threshold does not fire - the threshold is the line, and this rule is
        // about crossing it.
        if (amount.compareTo(THRESHOLD) <= 0) {
            return RuleResult.notTriggered(code());
        }

        return RuleResult.triggered(code(), POINTS,
                "Amount %s %s exceeded the %s threshold".formatted(
                        amount.toPlainString(),
                        context.transaction().getCurrency(),
                        THRESHOLD.toPlainString()));
    }
}
