package com.banking.enums;

/**
 * User roles for Spring Security authorization.
 *
 * <p>Prefixed with {@code ROLE_} as required by Spring Security's
 * {@code hasRole()} method which strips the prefix automatically.
 *
 * @author Mini Banking API
 * @version 1.0
 */
public enum Role {

    /** Standard customer — can manage only their own accounts and transactions. */
    ROLE_USER,

    /** Bank administrator — unrestricted read access across all resources. */
    ROLE_ADMIN
}
