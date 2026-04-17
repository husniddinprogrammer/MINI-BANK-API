package com.banking.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an authenticated user attempts to access a resource
 * that belongs to another user.
 *
 * <p>Maps to HTTP 403 Forbidden — distinguished from 401 Unauthorized
 * (which means "not authenticated") by carrying a concrete subject/resource.
 *
 * @author Mini Banking API
 * @version 1.0
 */
public class UnauthorizedAccessException extends BankingException {

    /**
     * @param message specific reason for the access denial (safe to surface to the caller)
     */
    public UnauthorizedAccessException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
