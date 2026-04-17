package com.banking.dto.response;

import com.banking.enums.Currency;
import com.banking.enums.TransactionStatus;
import com.banking.enums.TransactionType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Public projection of a {@link com.banking.entity.Transaction} entity.
 *
 * <p>Balance snapshots are included to enable clients to show "balance before/after"
 * on transaction detail screens without additional API calls.
 *
 * @author Mini Banking API
 * @version 1.0
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionResponse(

    UUID id,
    String referenceNumber,
    UUID sourceAccountId,
    UUID targetAccountId,
    TransactionType type,
    TransactionStatus status,
    BigDecimal amount,
    BigDecimal fee,
    BigDecimal balanceBeforeSource,
    BigDecimal balanceAfterSource,
    BigDecimal balanceBeforeTarget,
    BigDecimal balanceAfterTarget,
    Currency currency,
    String description,
    String failureReason,
    LocalDateTime processedAt,
    LocalDateTime createdAt

) {}
