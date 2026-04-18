package com.banking.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when the {@code X-Idempotency-Key} header is present but does not
 * conform to UUID v4 format (xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx).
 *
 * <p>Using a dedicated exception (rather than a plain {@link BankingException})
 * makes the failure reason explicit in logs and allows callers to catch it
 * narrowly in tests.
 *
 * @author Mini Banking API
 * @version 1.0
 */
public class InvalidIdempotencyKeyException extends BankingException {

    public InvalidIdempotencyKeyException(String key) {
        super(
            String.format(
                "Invalid X-Idempotency-Key '%s': must be a valid UUID (xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx)",
                key),
            HttpStatus.BAD_REQUEST
        );
    }
}
