package com.banking.repository;

import com.banking.entity.Account;
import com.banking.enums.AccountStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access layer for {@link Account} entities.
 *
 * <p>The {@code findByIdWithPessimisticLock} query acquires a {@code SELECT … FOR UPDATE}
 * lock at the database level. This is used during transfers to prevent concurrent
 * balance modifications (race condition / double-spend prevention).
 *
 * @author Mini Banking API
 * @version 1.0
 */
public interface AccountRepository extends JpaRepository<Account, UUID> {

    /**
     * Returns all accounts belonging to the given user, ordered by creation time.
     *
     * @param ownerId the user's UUID
     * @return list of accounts (may be empty, never null)
     */
    @Query("SELECT a FROM Account a WHERE a.owner.id = :ownerId ORDER BY a.createdAt ASC")
    List<Account> findAllByOwnerId(@Param("ownerId") UUID ownerId);

    /**
     * Checks whether an account number is already in use.
     * Used by the account-number generation retry loop.
     *
     * @param accountNumber the candidate account number
     * @return {@code true} if taken
     */
    boolean existsByAccountNumber(String accountNumber);

    /**
     * Finds an account by its 16-digit number.
     *
     * @param accountNumber the account number
     * @return the account or empty
     */
    Optional<Account> findByAccountNumber(String accountNumber);

    /**
     * Acquires a PESSIMISTIC_WRITE (SELECT … FOR UPDATE) lock on the account row.
     *
     * <p>Used during fund transfers to serialize concurrent modifications on the
     * same account. Combined with consistent lock ordering (lower UUID first)
     * in the service layer to prevent deadlocks.
     *
     * @param id the account UUID
     * @return the locked account or empty
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdWithPessimisticLock(@Param("id") UUID id);

    /**
     * Counts active accounts for a user — used to enforce the per-user account limit.
     *
     * @param ownerId the user's UUID
     * @param status  the status to count (typically ACTIVE)
     * @return number of accounts in that status
     */
    @Query("SELECT COUNT(a) FROM Account a WHERE a.owner.id = :ownerId AND a.status = :status")
    long countByOwnerIdAndStatus(@Param("ownerId") UUID ownerId, @Param("status") AccountStatus status);

    /**
     * Checks whether a user already has a primary account.
     * At most one primary account is allowed per user.
     *
     * @param ownerId the user's UUID
     * @return {@code true} if a primary account exists
     */
    @Query("SELECT COUNT(a) > 0 FROM Account a WHERE a.owner.id = :ownerId AND a.isPrimary = TRUE")
    boolean existsPrimaryAccountForUser(@Param("ownerId") UUID ownerId);

    /**
     * Finds the primary account for a user.
     *
     * @param ownerId the user's UUID
     * @return the primary account or empty
     */
    @Query("SELECT a FROM Account a WHERE a.owner.id = :ownerId AND a.isPrimary = TRUE")
    Optional<Account> findPrimaryAccountByOwnerId(@Param("ownerId") UUID ownerId);
}
