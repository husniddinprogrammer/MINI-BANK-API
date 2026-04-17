package com.banking.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a creation request would violate a uniqueness constraint
 * (e.g. duplicate email, duplicate account number).
 *
 * <p>Maps to HTTP 409 Conflict.
 *
 * @author Mini Banking API
 * @version 1.0
 */
public class DuplicateResourceException extends BankingException {

    /**
     * @param resourceName the type of resource (e.g. "User")
     * @param fieldName    the duplicate field (e.g. "email")
     * @param fieldValue   the conflicting value
     */
    public DuplicateResourceException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s already exists with %s: '%s'", resourceName, fieldName, fieldValue),
            HttpStatus.CONFLICT);
    }
}
