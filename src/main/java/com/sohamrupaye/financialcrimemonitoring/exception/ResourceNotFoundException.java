package com.sohamrupaye.financialcrimemonitoring.exception;

/**
 * Thrown when a requested record does not exist.
 *
 * <p>Unchecked, because Spring only rolls a transaction back automatically for
 * unchecked exceptions. It carries no HTTP status — mapping it to a 404 is
 * {@code GlobalExceptionHandler}'s job, which keeps the service layer usable
 * from a scheduled job or a message listener.
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
