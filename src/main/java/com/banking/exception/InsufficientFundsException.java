package com.banking.exception;

import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

/**
 * Thrown when an account does not have sufficient balance to complete a transaction.
 *
 * <p>Maps to HTTP 422 Unprocessable Entity — the request was valid but the business
 * rule (sufficient balance) could not be satisfied.
 *
 * @author Mini Banking API
 * @version 1.0
 */
public class InsufficientFundsException extends BankingException {

    /**
     * @param available the current account balance
     * @param required  the amount required (including fees)
     */
    public InsufficientFundsException(BigDecimal available, BigDecimal required) {
        super(String.format("Insufficient funds. Available: %s, Required: %s", available, required),
            HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
