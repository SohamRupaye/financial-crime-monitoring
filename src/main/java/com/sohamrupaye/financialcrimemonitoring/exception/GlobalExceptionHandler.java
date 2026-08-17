package com.sohamrupaye.financialcrimemonitoring.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

/**
 * Turns exceptions into HTTP responses in one place.
 *
 * <p>{@code @RestControllerAdvice} registers these handlers across every
 * controller, which is what keeps controllers free of try/catch. Without it,
 * any uncaught exception becomes an opaque 500.
 *
 * <p>Responses use {@link ProblemDetail} — the RFC 9457 {@code application/
 * problem+json} format built into Spring 6+. Prefer it to a hand-rolled error
 * record: clients get a documented, predictable shape for free.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException exception) {
        // Expected outcome, not a fault — debug level, and no stack trace.
        log.debug("Resource not found: {}", exception.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Resource not found");
        return problem;
    }

    @ExceptionHandler(DuplicateResourceException.class)
    ProblemDetail handleDuplicate(DuplicateResourceException exception) {
        log.debug("Duplicate resource: {}", exception.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Resource already exists");
        return problem;
    }

    /**
     * Raised by Spring when an argument annotated {@code @Valid} fails its
     * constraints. The per-field messages are collected so a client can show them
     * next to the offending inputs instead of guessing from one flat string.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new TreeMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage()));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "One or more fields are invalid");
        problem.setTitle("Validation failed");
        problem.setProperty("errors", fieldErrors);
        return problem;
    }

    /**
     * Raised when Spring cannot convert a path variable or query parameter to the
     * declared type — {@code ?riskLevel=BOGUS} for an enum, or a non-numeric value
     * where a number is expected.
     *
     * <p>Without this handler such a request falls through to
     * {@link #handleUnexpected} and is reported as a 500, blaming the server for
     * what is plainly a bad request. Enum values are listed back to the caller,
     * since the whole set is public API anyway.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        Class<?> requiredType = exception.getRequiredType();

        String detail = requiredType != null && requiredType.isEnum()
                ? "'%s' is not a valid %s. Allowed values: %s".formatted(
                        exception.getValue(),
                        exception.getName(),
                        Arrays.toString(requiredType.getEnumConstants()))
                : "'%s' is not a valid value for '%s'".formatted(
                        exception.getValue(), exception.getName());

        log.debug("Parameter type mismatch: {}", detail);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Invalid parameter");
        return problem;
    }

    /**
     * Last resort. Logs the full stack trace server-side but returns a generic
     * message, because exception text routinely contains table names, SQL, and
     * file paths that must not reach a client.
     *
     * <p>A handler this broad is a liability: anything it catches is reported as a
     * server fault. When a 500 shows up for what is really a client mistake, the
     * fix is a specific handler above this one — as with the type-mismatch case.
     */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) {
        log.error("Unhandled exception", exception);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problem.setTitle("Internal server error");
        return problem;
    }
}
