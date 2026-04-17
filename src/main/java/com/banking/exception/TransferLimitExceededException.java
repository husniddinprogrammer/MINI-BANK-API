package com.banking.exception;

import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

/**
 * Thrown when a transfer would exceed the account's daily or monthly limit.
 *
 * <p>Maps to HTTP 422 Unprocessable Entity.
 *
 * @author Mini Banking API
 * @version 1.0
 */
public class TransferLimitExceededException extends BankingException {

    /**
     * @param limitType   "daily" or "monthly"
     * @param limit       the configured limit amount
     * @param accumulated amount already transferred in the current period
     * @param requested   the requested transfer amount
     */
    public TransferLimitExceededException(
        String limitType,
        BigDecimal limit,
        BigDecimal accumulated,
        BigDecimal requested
    ) {
        super(String.format(
            "%s transfer limit exceeded. Limit: %s, Already used: %s, Requested: %s",
            limitType, limit, accumulated, requested),
            HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
