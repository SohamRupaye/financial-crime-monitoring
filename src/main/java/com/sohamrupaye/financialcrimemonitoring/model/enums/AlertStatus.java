package com.sohamrupaye.financialcrimemonitoring.model.enums;

/**
 * Where an alert sits in an analyst's queue.
 *
 * <p>{@code RESOLVED} and {@code FALSE_POSITIVE} are both terminal, and the
 * distinction between them is the useful part: a false positive says the rules
 * were wrong, which is what a threshold gets tuned against.
 */
public enum AlertStatus {

    OPEN,
    ACKNOWLEDGED,
    INVESTIGATING,
    RESOLVED,
    FALSE_POSITIVE;

    public boolean isTerminal() {
        return this == RESOLVED || this == FALSE_POSITIVE;
    }
}
