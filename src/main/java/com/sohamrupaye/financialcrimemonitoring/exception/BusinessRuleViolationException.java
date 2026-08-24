package com.sohamrupaye.financialcrimemonitoring.exception;

/**
 * Thrown when a request is well-formed and passes field validation but breaks a
 * rule of the domain — posting to a closed account, say.
 *
 * <p>Distinct from a validation failure on purpose. Bean Validation can only see
 * one field at a time; this covers the rules that need the database to decide.
 * {@code GlobalExceptionHandler} maps it to 422.
 */
public class BusinessRuleViolationException extends RuntimeException {

    public BusinessRuleViolationException(String message) {
        super(message);
    }
}
