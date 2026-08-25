package com.sohamrupaye.financialcrimemonitoring.rules;

/**
 * What one rule concluded about one transaction.
 *
 * <p>Every rule returns one of these whether or not it fired. Recording the
 * quiet ones is what makes it possible later to ask why an alert did *not*
 * happen, and to tune a threshold against real outcomes.
 */
public record RuleResult(RuleCode code, boolean triggered, int points, String reason) {

    public RuleResult {
        if (code == null) {
            throw new IllegalArgumentException("code is required");
        }
        if (points < 0) {
            throw new IllegalArgumentException("points cannot be negative");
        }
        // A rule that fires without adding risk or explaining itself is a bug,
        // not a design option - it would inflate the triggered count while
        // leaving an analyst nothing to read.
        if (triggered && (points == 0 || reason == null || reason.isBlank())) {
            throw new IllegalArgumentException(
                    "a triggered result needs both points and a reason: " + code);
        }
        if (!triggered && points > 0) {
            throw new IllegalArgumentException("an untriggered result cannot carry points: " + code);
        }
    }

    public static RuleResult notTriggered(RuleCode code) {
        return new RuleResult(code, false, 0, null);
    }

    public static RuleResult triggered(RuleCode code, int points, String reason) {
        return new RuleResult(code, true, points, reason);
    }
}
