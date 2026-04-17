package com.banking.entity;

import com.banking.entity.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Persisted refresh token record for the JWT token rotation strategy.
 *
 * <p>Security design:
 * <ul>
 *   <li>The raw token is NEVER stored. Only a SHA-256 hash is persisted.
 *       The raw token is returned to the client once and discarded server-side.</li>
 *   <li>On each refresh, the old token is revoked and a new one issued (rotation).
 *       This limits the window of abuse if a refresh token is stolen.</li>
 *   <li>{@code deviceInfo} is derived from the User-Agent header to detect
 *       token usage from unexpected clients (anomaly detection foundation).</li>
 *   <li>{@code ipAddress} supports geo-anomaly alerting in future iterations.</li>
 * </ul>
 *
 * @author Mini Banking API
 * @version 1.0
 */
@Entity
@Table(
    name = "refresh_tokens",
    indexes = {
        @Index(name = "idx_refresh_tokens_token", columnList = "token", unique = true),
        @Index(name = "idx_refresh_tokens_user_id", columnList = "user_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken extends BaseEntity {

    /** SHA-256 hash of the raw token. Raw token lives only in the HTTP response body. */
    @Column(name = "token", nullable = false, unique = true, length = 64)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "expiry_date", nullable = false)
    private LocalDateTime expiryDate;

    /** Set to true when this token has been explicitly invalidated (logout / rotation). */
    @Column(name = "revoked", nullable = false)
    @Builder.Default
    private boolean revoked = false;

    /** Truncated User-Agent string for device fingerprinting. */
    @Column(name = "device_info", length = 200)
    private String deviceInfo;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    // ── Business methods ─────────────────────────────────────────────────────

    /**
     * @return {@code true} if the token's expiry time is in the past.
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiryDate);
    }

    /**
     * A token is valid only if it has not been explicitly revoked AND has not expired.
     *
     * @return {@code true} if the token can be used to issue a new access token.
     */
    public boolean isValid() {
        return !revoked && !isExpired();
    }
}
