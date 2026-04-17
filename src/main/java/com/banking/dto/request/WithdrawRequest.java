package com.banking.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload for withdrawing funds from an account.
 *
 * @param accountId   the source account UUID
 * @param amount      must be strictly positive
 * @param description optional note (max 255 chars)
 *
 * @author Mini Banking API
 * @version 1.0
 */
public record WithdrawRequest(

    @NotNull(message = "Account ID is required")
    UUID accountId,

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.0001", message = "Withdrawal amount must be greater than zero")
    BigDecimal amount,

    @Size(max = 255, message = "Description must not exceed 255 characters")
    String description

) {}
