package com.banking.enums;

/**
 * Defines the nature of a financial transaction.
 *
 * @author Mini Banking API
 * @version 1.0
 */
public enum TransactionType {

    /** Cash or external credit added to an account. Source account is null. */
    DEPOSIT,

    /** Cash or value removed from an account. Target account is null. */
    WITHDRAWAL,

    /** Movement of funds between two internal accounts. Both source and target are set. */
    TRANSFER
}
