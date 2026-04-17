package com.banking.dto.request;

import com.banking.enums.AccountType;
import com.banking.enums.Currency;
import jakarta.validation.constraints.NotNull;

/**
 * Payload for opening a new bank account.
 *
 * <p>The account number is generated server-side; the caller only
 * specifies type, currency, and primary-flag preference.
 *
 * @param accountType currency type (SAVINGS or CHECKING)
 * @param currency    currency denomination (UZS, USD, EUR)
 * @param isPrimary   whether this should become the user's primary account
 *
 * @author Mini Banking API
 * @version 1.0
 */
public record CreateAccountRequest(

    @NotNull(message = "Account type is required")
    AccountType accountType,

    @NotNull(message = "Currency is required")
    Currency currency,

    boolean isPrimary

) {}
