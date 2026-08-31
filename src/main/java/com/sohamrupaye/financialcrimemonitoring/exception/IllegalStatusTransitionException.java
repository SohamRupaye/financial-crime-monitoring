package com.sohamrupaye.financialcrimemonitoring.exception;

/**
 * Thrown when a caller asks for a status change the workflow does not allow.
 *
 * <p>Its own type rather than a {@link BusinessRuleViolationException} because it
 * maps to 409: the request conflicts with the resource's current state, and the
 * same request might well succeed once the alert has moved on.
 */
public class IllegalStatusTransitionException extends RuntimeException {

    public IllegalStatusTransitionException(String message) {
        super(message);
    }
}
