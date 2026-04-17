package com.banking.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an operation is attempted on a FROZEN or CLOSED account.
 *
 * <p>Maps to HTTP 422 Unprocessable Entity.
 *
 * @author Mini Banking API
 * @version 1.0
 */
public class AccountFrozenException extends BankingException {

    /**
     * @param accountNumber the masked account number (last 4 digits)
     * @param status        the current account status (FROZEN or CLOSED)
     */
    public AccountFrozenException(String accountNumber, String status) {
        super(String.format("Account ending in %s is %s and cannot process transactions",
            accountNumber, status), HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
