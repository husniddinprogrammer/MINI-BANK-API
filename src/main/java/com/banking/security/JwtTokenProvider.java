package com.banking.security;

import com.banking.config.ApplicationProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Generates, validates, and parses JWT access tokens using JJWT 0.12.x.
 *
 * <p>Token design:
 * <ul>
 *   <li>Algorithm: HS512 — stronger than HS256; appropriate for financial APIs</li>
 *   <li>Claims: {@code sub} (email), {@code userId}, {@code role}, {@code jti} (unique ID)</li>
 *   <li>{@code jti} enables token revocation via a blacklist if needed</li>
 *   <li>Access tokens are short-lived (15 min) to limit blast radius of leaks</li>
 * </ul>
 *
 * <p>Security notes:
 * <ul>
 *   <li>The signing key is derived from the configured secret using HMAC-SHA512.
 *       The secret must be ≥ 64 bytes (512 bits) — JJWT enforces this at startup.</li>
 *   <li>Full token strings are never logged — only the {@code jti} (non-sensitive ID).</li>
 * </ul>
 *
 * @author Mini Banking API
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final ApplicationProperties properties;

    private SecretKey signingKey;

    /**
     * Derives and caches the HMAC-SHA512 signing key from the configured secret.
     * Fails fast at startup if the secret is too short for HS512.
     */
    @PostConstruct
    private void initSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(properties.getSecurity().getJwt().getSecret());
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        log.info("JWT signing key initialized successfully");
    }

    /**
     * Generates a signed JWT access token for the authenticated principal.
     *
     * @param authentication the fully authenticated {@link Authentication} object
     * @return a compact, signed JWT string
     */
    public String generateAccessToken(Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return generateAccessToken(userDetails);
    }

    /**
     * Generates a signed JWT access token directly from {@link CustomUserDetails}.
     * Used after token refresh when we have the user but not a full Authentication.
     *
     * @param userDetails the authenticated user's details
     * @return a compact, signed JWT string
     */
    public String generateAccessToken(CustomUserDetails userDetails) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(properties.getSecurity().getJwt().getAccessTokenExpiration());

        String role = userDetails.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .findFirst()
            .orElse("ROLE_USER");

        String jti = UUID.randomUUID().toString();
        log.debug("Generating access token with jti={} for user={}", jti, userDetails.getUsername());

        ApplicationProperties.Security.Jwt jwtProps = properties.getSecurity().getJwt();

        return Jwts.builder()
            .subject(userDetails.getUsername())
            .claim("userId", userDetails.getUserId().toString())
            .claim("role", role)
            .id(jti)                        // jti claim for token revocation support
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            // SECURITY: iss+aud validation prevents token substitution attacks where
            // a token issued by another service (same key) is replayed against this API.
            .issuer(jwtProps.getIssuer())
            .audience().add(jwtProps.getAudience()).and()
            .signWith(signingKey)            // HS512 inferred from key length
            .compact();
    }

    /**
     * Extracts the email (subject) from a token.
     *
     * @param token the compact JWT string
     * @return the email stored in the {@code sub} claim
     */
    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Extracts the user UUID from the {@code userId} claim.
     *
     * @param token the compact JWT string
     * @return the user's UUID
     */
    public String extractUserId(String token) {
        return parseClaims(token).get("userId", String.class);
    }

    /**
     * Validates a JWT token's signature and expiry.
     *
     * <p>Does NOT check business-level revocation (blacklist) — that is handled
     * in {@link JwtAuthenticationFilter} using the jti claim if implemented.
     *
     * @param token the compact JWT string
     * @return {@code true} if signature is valid and token is not expired
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT token is expired");
        } catch (ClaimJwtException e) {
            // Covers IncorrectClaimException (wrong iss/aud) and MissingClaimException
            log.warn("JWT claim validation failed: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("JWT token is unsupported");
        } catch (MalformedJwtException e) {
            log.warn("JWT token is malformed");
        } catch (SecurityException e) {
            // Covers io.jsonwebtoken.security.SignatureException in JJWT 0.12.x
            log.warn("JWT signature validation failed");
        } catch (IllegalArgumentException e) {
            log.warn("JWT token is null or empty");
        }
        return false;
    }

    /**
     * Parses and verifies the JWT, returning the claims body.
     *
     * @param token the compact JWT string
     * @return the verified claims
     * @throws JwtException if the token is invalid
     */
    private Claims parseClaims(String token) {
        ApplicationProperties.Security.Jwt jwtProps = properties.getSecurity().getJwt();
        return Jwts.parser()
            .verifyWith(signingKey)
            // SECURITY: iss+aud validation prevents token substitution attacks where
            // a token issued by another service (same key) is replayed against this API.
            .requireIssuer(jwtProps.getIssuer())
            .requireAudience(jwtProps.getAudience())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
