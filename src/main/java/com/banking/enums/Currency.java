package com.banking.enums;

/**
 * Supported currencies in the banking system.
 *
 * <p>Cross-currency transfers are intentionally not supported in V1 to avoid
 * exchange-rate risk and FX compliance complexity. Attempting a cross-currency
 * transfer throws {@code UnsupportedOperationException}.
 *
 * @author Mini Banking API
 * @version 1.0
 */
public enum Currency {

    /** Uzbekistani Som — primary currency for domestic operations. */
    UZS,

    /** United States Dollar — supported for USD-denominated accounts. */
    USD,

    /** Euro — supported for EUR-denominated accounts. */
    EUR
}
