package com.sohamrupaye.financialcrimemonitoring.rules;

import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Flags a burst of activity on one account.
 *
 * <p>Individually unremarkable transactions can still be a signal when there are
 * twenty of them in ten minutes — automated or scripted movement looks like this,
 * and no amount threshold will catch it.
 */
@Component
public class VelocityRule implements AmlRule {

    static final Duration WINDOW = Duration.ofMinutes(10);

    /** Exceeding this count triggers; matching it exactly does not. */
    static final int MAX_TRANSACTIONS = 10;

    private static final int POINTS = 20;

    @Override
    public RuleCode code() {
        return RuleCode.VELOCITY;
    }

    @Override
    public RuleResult evaluate(RuleContext context) {
        // The history port excludes the transaction under evaluation, so it is
        // added back here: the question is how much activity there has been
        // including this movement, not before it.
        int count = context.precedingWindow(WINDOW).size() + 1;

        if (count <= MAX_TRANSACTIONS) {
            return RuleResult.notTriggered(code());
        }

        return RuleResult.triggered(code(), POINTS,
                "%d transactions in %d minutes exceeded the limit of %d".formatted(
                        count, WINDOW.toMinutes(), MAX_TRANSACTIONS));
    }
}
