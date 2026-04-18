package com.banking.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload for transferring funds between two internal accounts.
 *
 * <p>The caller must supply an {@code X-Idempotency-Key} header (enforced at the
 * controller level) to guarantee exactly-once processing under network retries.
 *
 * @param sourceAccountId UUID of the account to debit (must belong to the caller)
 * @param targetAccountId UUID of the account to credit (can belong to any user)
 * @param amount          strictly positive transfer amount
 * @param description     optional payment reference (max 255 chars)
 *
 * @author Mini Banking API
 * @version 1.0
 */
public record TransferRequest(

    @NotNull(message = "Source account ID is required")
    UUID sourceAccountId,

    @NotNull(message = "Target account ID is required")
    UUID targetAccountId,

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.0001", message = "Transfer amount must be greater than zero")
    @DecimalMax(value = "500000000", message = "Transfer amount must not exceed the monthly limit of 500,000,000")
    BigDecimal amount,

    @Size(max = 255, message = "Description must not exceed 255 characters")
    String description

) {}
