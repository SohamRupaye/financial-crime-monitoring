package com.sohamrupaye.financialcrimemonitoring.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.databind.exc.InvalidFormatException;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

/**
 * Turns exceptions into HTTP responses in one place, which is what keeps
 * controllers free of try/catch.
 *
 * <p>Responses use {@link ProblemDetail} — RFC 9457 {@code problem+json} — so
 * clients get a documented, predictable error shape rather than a hand-rolled
 * one.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException exception) {
        // Expected outcome, not a fault - debug level, and no stack trace.
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
     * 409, not 422: the request is fine and would have worked from a different
     * starting state. What is wrong is the state, not the request.
     */
    @ExceptionHandler(IllegalStatusTransitionException.class)
    ProblemDetail handleIllegalStatusTransition(IllegalStatusTransitionException exception) {
        log.debug("Illegal status transition: {}", exception.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Status transition not allowed");
        return problem;
    }

    /**
     * 422 rather than 400: the request was syntactically fine and every field
     * passed validation, so the problem is not with the request's form but with
     * what it asks for.
     */
    @ExceptionHandler(BusinessRuleViolationException.class)
    ProblemDetail handleBusinessRuleViolation(BusinessRuleViolationException exception) {
        log.debug("Business rule violation: {}", exception.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
        problem.setTitle("Request cannot be processed");
        return problem;
    }

    /**
     * Per-field messages are collected so a client can show them next to the
     * offending inputs instead of guessing from one flat string.
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
     * {@code ?riskLevel=BOGUS}, or a non-numeric value where a number is expected.
     *
     * <p>Without this handler such a request falls through to
     * {@link #handleUnexpected} and is reported as a 500, blaming the server for
     * a plainly bad request.
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
     * Raised when the body cannot be deserialised at all — malformed JSON, or a
     * value that does not fit the field it landed on. Same class of bug as the
     * handler above: without it, a client mistake is reported as a server fault.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadableBody(HttpMessageNotReadableException exception) {
        log.debug("Unreadable request body: {}", exception.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, describeUnreadableBody(exception));
        problem.setTitle("Malformed request");
        return problem;
    }

    /**
     * An unrecognised enum value is the common case and worth naming, since the
     * allowed set is public API anyway. Everything else gets a generic message —
     * Jackson's own text carries class names and internal paths, which must not
     * reach a client.
     */
    private static String describeUnreadableBody(HttpMessageNotReadableException exception) {
        for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof InvalidFormatException invalid
                    && invalid.getTargetType() != null
                    && invalid.getTargetType().isEnum()) {

                return "'%s' is not a valid %s. Allowed values: %s".formatted(
                        invalid.getValue(),
                        invalid.getTargetType().getSimpleName(),
                        Arrays.toString(invalid.getTargetType().getEnumConstants()));
            }
        }
        return "Request body is not valid JSON, or a field has the wrong type";
    }

    /**
     * Last resort. Logs the stack trace but returns a generic message, because
     * exception text routinely carries table names, SQL and file paths.
     *
     * <p>A handler this broad is a liability: anything it catches is reported as
     * a server fault. When a 500 turns out to be a client mistake, the fix is a
     * specific handler above this one — as with the two cases just above.
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
