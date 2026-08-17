package com.sohamrupaye.financialcrimemonitoring.exception;

/**
 * Thrown when a requested record does not exist.
 *
 * <p>Extends {@code RuntimeException}, not {@code Exception}, on purpose. A
 * checked exception would force {@code throws} declarations up through the
 * service and controller, and — importantly — Spring only rolls a transaction
 * back automatically for unchecked exceptions.
 *
 * <p>It carries no HTTP status. Mapping this to a 404 is
 * {@code GlobalExceptionHandler}'s job, which keeps the service layer free of
 * web concerns and reusable from a scheduled job or message listener.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    /** Convenience for the common "type + identifier" phrasing. */
    public static ResourceNotFoundException of(String resource, String identifier) {
        return new ResourceNotFoundException("%s not found: %s".formatted(resource, identifier));
    }
}
