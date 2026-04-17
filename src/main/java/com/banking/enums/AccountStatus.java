package com.banking.enums;

/**
 * Lifecycle states of a bank account.
 *
 * <p>State transitions are enforced in the service layer:
 * ACTIVE → FROZEN → ACTIVE (re-enabled by admin)
 * ACTIVE → CLOSED  (terminal; cannot be reopened)
 *
 * @author Mini Banking API
 * @version 1.0
 */
public enum AccountStatus {

    /** Account is operational and can send/receive funds. */
    ACTIVE,

    /**
     * Account is temporarily suspended.
     * Deposits may still be received (configurable), but withdrawals/transfers are blocked.
     */
    FROZEN,

    /** Account is permanently closed. No further transactions are possible. */
    CLOSED
}
