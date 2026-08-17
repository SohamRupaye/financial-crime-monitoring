package com.sohamrupaye.financialcrimemonitoring.exception;

/**
 * Thrown when creating a record would collide with one that already exists,
 * e.g. a second customer with the same email address.
 *
 * <p>Maps to HTTP 409 Conflict in {@code GlobalExceptionHandler} — not 400. The
 * request was well-formed; it simply lost a race with existing state.
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
