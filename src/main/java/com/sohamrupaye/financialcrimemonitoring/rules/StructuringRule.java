package com.sohamrupaye.financialcrimemonitoring.rules;

import com.sohamrupaye.financialcrimemonitoring.model.Transaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Flags one large movement deliberately broken into several smaller ones.
 *
 * <p>This is the rule that catches what {@link LargeAmountRule} is designed to
 * miss. Someone moving ₹1,955,000 as four transfers of roughly ₹490,000 stays
 * under the reporting threshold on every single one of them, so no rule looking
 * at a transaction in isolation will ever see it.
 *
 * <p>Three conditions have to hold together, and each of them is what stops a
 * different kind of false positive:
 *
 * <ul>
 *   <li>every transaction sits between the near-threshold floor and the
 *       large-amount threshold — otherwise a week of grocery payments adds up and
 *       looks like structuring;</li>
 *   <li>there are at least {@code minTransactions} of them — two large transfers
 *       on one day is ordinary business;</li>
 *   <li>the total clears the threshold that was being avoided — if the sum would
 *       not have been reportable anyway, nothing was evaded.</li>
 * </ul>
 */
@Component
public class StructuringRule implements AmlRule {

    private final AmlProperties.Structuring config;

    /**
     * Reads the large-amount threshold as well as its own settings. Structuring
     * is defined relative to the line being evaded, so the two cannot be
     * configured independently without the rule becoming nonsense.
     */
    private final BigDecimal largeAmountThreshold;

    public StructuringRule(AmlProperties properties) {
        this.config = properties.structuring();
        this.largeAmountThreshold = properties.largeAmount().threshold();

        if (config.nearThresholdFloor().compareTo(largeAmountThreshold) >= 0) {
            throw new IllegalStateException(
                    "aml.structuring.near-threshold-floor must be below "
                            + "aml.large-amount.threshold, otherwise no amount can ever qualify");
        }
    }

    @Override
    public RuleCode code() {
        return RuleCode.STRUCTURING;
    }

    @Override
    public RuleResult evaluate(RuleContext context) {
        Transaction current = context.transaction();

        // If this movement is itself over the threshold then nothing was being
        // avoided, and LargeAmountRule already has it.
        if (!isNearThreshold(current.getAmount())) {
            return RuleResult.notTriggered(code());
        }

        List<Transaction> qualifying = context.precedingWindow(config.window()).stream()
                .filter(transaction -> isNearThreshold(transaction.getAmount()))
                .toList();

        int count = qualifying.size() + 1;
        if (count < config.minTransactions()) {
            return RuleResult.notTriggered(code());
        }

        BigDecimal total = qualifying.stream()
                .map(Transaction::getAmount)
                .reduce(current.getAmount(), BigDecimal::add);

        if (total.compareTo(largeAmountThreshold) <= 0) {
            return RuleResult.notTriggered(code());
        }

        return RuleResult.triggered(code(), config.points(),
                ("%d transactions totalling %s %s in %d hours, each individually below "
                        + "the %s threshold").formatted(
                        count,
                        total.toPlainString(),
                        current.getCurrency(),
                        config.window().toHours(),
                        largeAmountThreshold.toPlainString()));
    }

    /**
     * Both bounds inclusive of the threshold itself: an amount exactly on the
     * line was still chosen to sit at it.
     */
    private boolean isNearThreshold(BigDecimal amount) {
        return amount.compareTo(config.nearThresholdFloor()) >= 0
                && amount.compareTo(largeAmountThreshold) <= 0;
    }
}
