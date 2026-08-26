package com.sohamrupaye.financialcrimemonitoring.rules;

import com.sohamrupaye.financialcrimemonitoring.model.Transaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
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
 *   <li>every transaction sits between {@link #NEAR_THRESHOLD_FLOOR} and the
 *       large-amount threshold — otherwise a week of grocery payments adds up and
 *       looks like structuring;</li>
 *   <li>there are at least {@link #MIN_TRANSACTIONS} of them — two large
 *       transfers on one day is ordinary business;</li>
 *   <li>the total clears the threshold that was being avoided — if the sum would
 *       not have been reportable anyway, nothing was evaded.</li>
 * </ul>
 */
@Component
public class StructuringRule implements AmlRule {

    static final Duration WINDOW = Duration.ofHours(24);

    /**
     * How close to the threshold an amount has to be to look deliberate. Set to
     * 80% of {@link LargeAmountRule#THRESHOLD}: someone structuring aims just
     * under the line, not far below it.
     */
    static final BigDecimal NEAR_THRESHOLD_FLOOR = new BigDecimal("400000");

    static final int MIN_TRANSACTIONS = 3;

    private static final int POINTS = 30;

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

        List<Transaction> qualifying = context.precedingWindow(WINDOW).stream()
                .filter(transaction -> isNearThreshold(transaction.getAmount()))
                .toList();

        int count = qualifying.size() + 1;
        if (count < MIN_TRANSACTIONS) {
            return RuleResult.notTriggered(code());
        }

        BigDecimal total = qualifying.stream()
                .map(Transaction::getAmount)
                .reduce(current.getAmount(), BigDecimal::add);

        if (total.compareTo(LargeAmountRule.THRESHOLD) <= 0) {
            return RuleResult.notTriggered(code());
        }

        return RuleResult.triggered(code(), POINTS,
                ("%d transactions totalling %s %s in %d hours, each individually below "
                        + "the %s threshold").formatted(
                        count,
                        total.toPlainString(),
                        current.getCurrency(),
                        WINDOW.toHours(),
                        LargeAmountRule.THRESHOLD.toPlainString()));
    }

    /**
     * Both bounds inclusive of the threshold itself: an amount exactly on the
     * line was still chosen to stay at it.
     *
     * <p>Reading {@code LargeAmountRule.THRESHOLD} rather than declaring its own
     * copy is deliberate — structuring is defined relative to the threshold being
     * evaded, so the two must move together.
     */
    private static boolean isNearThreshold(BigDecimal amount) {
        return amount.compareTo(NEAR_THRESHOLD_FLOOR) >= 0
                && amount.compareTo(LargeAmountRule.THRESHOLD) <= 0;
    }
}
