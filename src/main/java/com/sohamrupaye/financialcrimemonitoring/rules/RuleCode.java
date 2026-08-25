package com.sohamrupaye.financialcrimemonitoring.rules;

/**
 * Identifies a rule for storage and for ordering an explanation.
 *
 * <p>A code rather than the class name: rule results are persisted, and a stored
 * row must not break when a class is renamed. Declaration order is the order
 * reasons appear in an assessment, so it runs roughly from "what happened" to
 * "who did it".
 */
public enum RuleCode {

    LARGE_AMOUNT,
    VELOCITY,
    STRUCTURING,
    CUSTOMER_RISK,
    COUNTRY_RISK
}
