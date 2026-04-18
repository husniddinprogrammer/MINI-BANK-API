package com.banking.repository;

import com.banking.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access layer for {@link User} entities.
 *
 * <p>All queries use JPQL named parameters — never string concatenation —
 * to prevent SQL injection at the ORM layer.
 *
 * @author Mini Banking API
 * @version 1.0
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Finds a user by email (case-insensitive lookup).
     * Email is stored lowercase, so {@code LOWER(:email)} handles callers
     * who pass mixed-case values.
     *
     * @param email the email address to search for
     * @return the user wrapped in Optional, or empty if not found
     */
    @Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    Optional<User> findByEmail(@Param("email") String email);

    /**
     * Checks email uniqueness before registration without loading the full entity.
     *
     * @param email the candidate email
     * @return {@code true} if already taken
     */
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    boolean existsByEmail(@Param("email") String email);

    /**
     * Checks phone number uniqueness before registration.
     *
     * @param phoneNumber E.164 formatted phone number
     * @return {@code true} if already taken
     */
    boolean existsByPhoneNumber(String phoneNumber);

    /**
     * Increments the failed login counter atomically in a single UPDATE.
     * Avoids a read-then-write race condition that could under-count failures.
     *
     * @param userId the user's UUID
     */
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.failedLoginAttempts = u.failedLoginAttempts + 1 WHERE u.id = :userId")
    void incrementFailedLoginAttempts(@Param("userId") UUID userId);

    /**
     * Resets the failed login counter and removes any timed lockout on successful auth.
     *
     * @param userId the user's UUID
     */
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.failedLoginAttempts = 0, u.lockedUntil = NULL, u.accountNonLocked = TRUE WHERE u.id = :userId")
    void resetFailedLoginAttempts(@Param("userId") UUID userId);

    /**
     * Locks an account until the specified time after exceeding the failure threshold.
     *
     * @param userId      the user's UUID
     * @param lockedUntil the timestamp after which the account auto-unlocks
     */
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.accountNonLocked = FALSE, u.lockedUntil = :lockedUntil WHERE u.id = :userId")
    void lockUser(@Param("userId") UUID userId, @Param("lockedUntil") LocalDateTime lockedUntil);

    /**
     * Returns the current failed login attempt count directly from the DB.
     * Used after {@link #incrementFailedLoginAttempts} to avoid stale in-memory reads.
     *
     * @param userId the user's UUID
     * @return current failed attempt count
     */
    @Query("SELECT u.failedLoginAttempts FROM User u WHERE u.id = :userId")
    int getFailedLoginAttempts(@Param("userId") UUID userId);

    /**
     * Atomically increments the failed login counter and locks the account in one UPDATE
     * if the new count reaches the threshold.
     *
     * <p>A single SQL UPDATE eliminates the read-then-write race where two concurrent
     * login attempts could both read the same pre-increment value, each decide the
     * threshold is not yet reached, and both avoid locking the account (TOCTOU).
     *
     * @param userId    the user's UUID
     * @param threshold lock the account when failedLoginAttempts reaches this value
     * @param lockUntil timestamp after which the timed lockout expires
     */
    @Modifying
    @Transactional
    @Query("""
        UPDATE User u SET
          u.failedLoginAttempts = u.failedLoginAttempts + 1,
          u.accountNonLocked = CASE
              WHEN u.failedLoginAttempts + 1 >= :threshold
              THEN FALSE ELSE u.accountNonLocked END,
          u.lockedUntil = CASE
              WHEN u.failedLoginAttempts + 1 >= :threshold
              THEN :lockUntil ELSE u.lockedUntil END
        WHERE u.id = :userId
        """)
    void incrementFailedAttemptsAndLockIfThreshold(
        @Param("userId") UUID userId,
        @Param("threshold") int threshold,
        @Param("lockUntil") LocalDateTime lockUntil
    );
}
