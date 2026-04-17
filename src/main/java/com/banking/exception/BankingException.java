package com.banking.exception;

import org.springframework.http.HttpStatus;

/**
 * Base runtime exception for all domain-specific banking errors.
 *
 * <p>Carries an HTTP status to allow {@link GlobalExceptionHandler} to map
 * each exception subclass to the correct HTTP response code without a long
 * if-else chain.
 *
 * @author Mini Banking API
 * @version 1.0
 */
public class BankingException extends RuntimeException {

    private final HttpStatus status;

    /**
     * @param message human-readable error description
     * @param status  the HTTP status code to return in the error response
     */
    public BankingException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    /**
     * @param message human-readable error description
     * @param status  the HTTP status code to return
     * @param cause   the underlying exception for stack trace chaining
     */
    public BankingException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    /**
     * @return the HTTP status that should be used in the API error response
     */
    public HttpStatus getStatus() {
        return status;
    }
}
