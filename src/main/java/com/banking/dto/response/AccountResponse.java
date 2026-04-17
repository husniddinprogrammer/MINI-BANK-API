package com.banking.dto.response;

import com.banking.enums.AccountStatus;
import com.banking.enums.AccountType;
import com.banking.enums.Currency;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Public projection of an {@link com.banking.entity.Account} entity.
 *
 * <p>The full {@code owner} entity is NOT embedded — only {@code ownerId} is exposed
 * to avoid circular references and over-fetching.
 *
 * @author Mini Banking API
 * @version 1.0
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccountResponse(

    UUID id,
    String accountNumber,
    UUID ownerId,
    AccountType accountType,
    AccountStatus status,
    BigDecimal balance,
    Currency currency,
    BigDecimal dailyTransferLimit,
    BigDecimal monthlyTransferLimit,
    boolean isPrimary,
    LocalDateTime createdAt

) {}
