package com.banking.repository;

import com.banking.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Data access layer for {@link RefreshToken} entities.
 *
 * <p>The token stored in DB is always a SHA-256 hash of the raw value.
 * Lookups must hash the incoming raw token before calling these methods.
 *
 * @author Mini Banking API
 * @version 1.0
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /**
     * Finds a refresh token record by its hashed value.
     *
     * @param token the SHA-256 hash of the raw refresh token
     * @return the token record or empty
     */
    Optional<RefreshToken> findByToken(String token);

    /**
     * Revokes ALL refresh tokens for a user — used during logout.
     * A bulk UPDATE is preferred over loading all entities for performance.
     *
     * @param userId the user's UUID
     */
    @Modifying
    @Transactional
    @Query("UPDATE RefreshToken rt SET rt.revoked = TRUE WHERE rt.user.id = :userId AND rt.revoked = FALSE")
    void revokeAllByUserId(@Param("userId") UUID userId);

    /**
     * Counts non-revoked, non-expired tokens for a user.
     * Can be used to detect suspicious multi-device refresh token accumulation.
     *
     * @param userId the user's UUID
     * @return number of valid tokens
     */
    @Query("""
        SELECT COUNT(rt) FROM RefreshToken rt
        WHERE rt.user.id = :userId
          AND rt.revoked = FALSE
          AND rt.expiryDate > CURRENT_TIMESTAMP
        """)
    long countValidTokensByUserId(@Param("userId") UUID userId);

    /**
     * Deletes expired or revoked tokens for a user to keep the table lean.
     * Called during login or token rotation as a housekeeping step.
     *
     * @param userId the user's UUID
     */
    @Modifying
    @Transactional
    @Query("""
        DELETE FROM RefreshToken rt
        WHERE rt.user.id = :userId
          AND (rt.revoked = TRUE OR rt.expiryDate <= CURRENT_TIMESTAMP)
        """)
    void deleteExpiredOrRevokedByUserId(@Param("userId") UUID userId);
}
