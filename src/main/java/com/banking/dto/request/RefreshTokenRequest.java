package com.banking.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Contains the raw refresh token for token rotation.
 *
 * <p>The server hashes the incoming token with SHA-256 before DB lookup,
 * so the raw value is never persisted.
 *
 * @param refreshToken the raw refresh token returned at login time
 *
 * @author Mini Banking API
 * @version 1.0
 */
public record RefreshTokenRequest(

    @NotBlank(message = "Refresh token is required")
    String refreshToken

) {}
