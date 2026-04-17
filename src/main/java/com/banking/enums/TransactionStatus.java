package com.banking.enums;

/**
 * Processing lifecycle of a transaction record.
 *
 * <p>A transaction is created in PENDING state and transitions to
 * COMPLETED or FAILED within the same database transaction.
 * REVERSED is used for post-completion chargebacks (admin operation).
 *
 * @author Mini Banking API
 * @version 1.0
 */
public enum TransactionStatus {

    /** Transaction has been initiated but not yet settled. */
    PENDING,

    /** Transaction settled successfully; balances have been updated. */
    COMPLETED,

    /** Transaction was rejected due to business rule violation or system error. */
    FAILED,

    /** Previously completed transaction was reversed by an administrator. */
    REVERSED
}
