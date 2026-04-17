package com.banking.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

/**
 * Returned after successful authentication (login or token refresh).
 *
 * <p>The {@code accessToken} is short-lived (15 min) and sent with every API call.
 * The {@code refreshToken} is long-lived (7 days) and used only to obtain new access tokens.
 *
 * @param accessToken  signed JWT access token (HS512, 15-minute expiry)
 * @param refreshToken raw refresh token (client must store securely; server stores SHA-256 hash)
 * @param tokenType    always "Bearer" — included for OAuth2 compatibility
 * @param expiresIn    access token TTL in seconds (900)
 *
 * @author Mini Banking API
 * @version 1.0
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(

    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn

) {
    /** Canonical token type value required by RFC 6750. */
    public static final String BEARER = "Bearer";
}
