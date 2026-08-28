package com.sohamrupaye.financialcrimemonitoring.rules;

import org.springframework.stereotype.Component;

/**
 * Flags a burst of activity on one account.
 *
 * <p>Individually unremarkable transactions can still be a signal when there are
 * twenty of them in ten minutes — automated or scripted movement looks like this,
 * and no amount threshold will catch it.
 */
@Component
public class VelocityRule implements AmlRule {

    private final AmlProperties.Velocity config;

    public VelocityRule(AmlProperties properties) {
        this.config = properties.velocity();
    }

    @Override
    public RuleCode code() {
        return RuleCode.VELOCITY;
    }

    @Override
    public RuleResult evaluate(RuleContext context) {
        // The history port excludes the transaction under evaluation, so it is
        // added back here: the question is how much activity there has been
        // including this movement, not before it.
        int count = context.precedingWindow(config.window()).size() + 1;

        if (count <= config.maxTransactions()) {
            return RuleResult.notTriggered(code());
        }

        return RuleResult.triggered(code(), config.points(),
                "%d transactions in %d minutes exceeded the limit of %d".formatted(
                        count, config.window().toMinutes(), config.maxTransactions()));
    }
}
