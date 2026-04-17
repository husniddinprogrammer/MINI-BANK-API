package com.banking.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Credentials submitted for authentication.
 *
 * <p>Deliberately minimal — we intentionally do NOT return separate error messages
 * for "user not found" vs "wrong password" to prevent username enumeration.
 *
 * @param email    the registered email address
 * @param password the raw password (never stored or logged)
 *
 * @author Mini Banking API
 * @version 1.0
 */
public record LoginRequest(

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    String email,

    @NotBlank(message = "Password is required")
    String password

) {}
