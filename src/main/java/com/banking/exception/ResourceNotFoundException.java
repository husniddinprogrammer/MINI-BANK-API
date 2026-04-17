package com.banking.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a requested resource (user, account, transaction) does not exist.
 *
 * <p>Maps to HTTP 404 Not Found.
 *
 * @author Mini Banking API
 * @version 1.0
 */
public class ResourceNotFoundException extends BankingException {

    /**
     * @param resourceName the type of resource (e.g. "Account", "User")
     * @param fieldName    the lookup field (e.g. "id", "accountNumber")
     * @param fieldValue   the value that was not found
     */
    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s not found with %s: '%s'", resourceName, fieldName, fieldValue),
            HttpStatus.NOT_FOUND);
    }
}
