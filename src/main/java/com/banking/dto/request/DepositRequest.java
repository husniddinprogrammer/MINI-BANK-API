package com.banking.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload for depositing funds into an account.
 *
 * @param accountId   the target account UUID
 * @param amount      must be strictly positive (reject zero and negative)
 * @param description optional reference note (max 255 chars)
 *
 * @author Mini Banking API
 * @version 1.0
 */
public record DepositRequest(

    @NotNull(message = "Account ID is required")
    UUID accountId,

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.0001", message = "Deposit amount must be greater than zero")
    BigDecimal amount,

    @Size(max = 255, message = "Description must not exceed 255 characters")
    String description

) {}
