package com.banking.exception;

import jakarta.persistence.OptimisticLockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralized exception handler that converts exceptions into RFC 7807
 * {@link ProblemDetail} responses (Spring 6 native support).
 *
 * <p>Response format:
 * <pre>
 * {
 *   "type": "https://banking.com/errors/insufficient-funds",
 *   "title": "Insufficient Funds",
 *   "status": 422,
 *   "detail": "Insufficient funds. Available: 1000, Required: 5000",
 *   "instance": "/api/v1/transactions/transfer",
 *   "timestamp": "2024-01-15T10:30:00Z"
 * }
 * </pre>
 *
 * <p>Security note: stack traces are NEVER included in API responses — they are
 * logged server-side at ERROR level only.
 *
 * @author Mini Banking API
 * @version 1.0
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String TIMESTAMP_KEY = "timestamp";
    private static final String BASE_TYPE_URI = "https://banking.com/errors/";

    // ── Domain exceptions ────────────────────────────────────────────────────

    /**
     * Handles all custom banking domain exceptions.
     *
     * @param ex      the thrown {@link BankingException}
     * @param request the current web request for URI extraction
     * @return RFC 7807 problem detail with the exception's HTTP status
     */
    @ExceptionHandler(BankingException.class)
    public ProblemDetail handleBankingException(BankingException ex, WebRequest request) {
        log.warn("Banking domain exception: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        problem.setType(URI.create(BASE_TYPE_URI + toKebabCase(ex.getClass().getSimpleName())));
        problem.setTitle(toTitleCase(ex.getClass().getSimpleName()));
        problem.setProperty(TIMESTAMP_KEY, Instant.now());
        return problem;
    }

    // ── Validation exceptions ────────────────────────────────────────────────

    /**
     * Handles Bean Validation ({@code @Valid}) failures, collecting all field errors.
     *
     * @param ex the validation exception
     * @return 400 problem detail with a map of field → error message
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String message = error.getDefaultMessage();
            fieldErrors.put(fieldName, message);
        });

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setType(URI.create(BASE_TYPE_URI + "validation-error"));
        problem.setTitle("Validation Error");
        problem.setProperty(TIMESTAMP_KEY, Instant.now());
        problem.setProperty("fieldErrors", fieldErrors);
        return problem;
    }

    // ── Concurrency exceptions ───────────────────────────────────────────────

    /**
     * Handles optimistic lock conflicts (concurrent modification of the same account row).
     * Instructs the client to retry — the operation itself was valid, timing caused the failure.
     *
     * @param ex the optimistic lock exception (JPA or Spring ORM variant)
     * @return 409 Conflict problem detail
     */
    @ExceptionHandler({OptimisticLockException.class, ObjectOptimisticLockingFailureException.class})
    public ProblemDetail handleOptimisticLockException(Exception ex) {
        log.warn("Optimistic lock conflict: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "The account was modified by another request. Please retry the operation.");
        problem.setType(URI.create(BASE_TYPE_URI + "concurrent-modification"));
        problem.setTitle("Concurrent Modification");
        problem.setProperty(TIMESTAMP_KEY, Instant.now());
        return problem;
    }

    // ── Spring Security exceptions ───────────────────────────────────────────

    /**
     * Handles authentication failures (missing or invalid JWT).
     *
     * @param ex the authentication exception
     * @return 401 problem detail
     */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthenticationException(AuthenticationException ex) {
        log.warn("Authentication failure: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.UNAUTHORIZED, "Authentication required");
        problem.setType(URI.create(BASE_TYPE_URI + "authentication-error"));
        problem.setTitle("Unauthorized");
        problem.setProperty(TIMESTAMP_KEY, Instant.now());
        return problem;
    }

    /**
     * Handles authorization failures (authenticated but insufficient permissions).
     *
     * @param ex the access denied exception
     * @return 403 problem detail
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.FORBIDDEN, "You do not have permission to perform this action");
        problem.setType(URI.create(BASE_TYPE_URI + "access-denied"));
        problem.setTitle("Access Denied");
        problem.setProperty(TIMESTAMP_KEY, Instant.now());
        return problem;
    }

    // ── Catch-all ────────────────────────────────────────────────────────────

    /**
     * Catch-all handler for unexpected exceptions.
     * Logs the full stack trace but returns a generic message to the client
     * to avoid leaking internal details.
     *
     * @param ex the unexpected exception
     * @return 500 problem detail with a generic message
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        log.error("Unexpected error occurred", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred. Please contact support if the problem persists.");
        problem.setType(URI.create(BASE_TYPE_URI + "internal-error"));
        problem.setTitle("Internal Server Error");
        problem.setProperty(TIMESTAMP_KEY, Instant.now());
        return problem;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String toKebabCase(String className) {
        return className.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }

    private String toTitleCase(String className) {
        return className.replaceAll("([a-z])([A-Z])", "$1 $2");
    }
}
