package com.banking.repository;

import com.banking.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access layer for {@link Transaction} entities.
 *
 * <p>The daily/monthly limit queries aggregate COMPLETED transfers only —
 * PENDING or FAILED transactions do not count toward limits since no funds
 * were actually moved.
 *
 * @author Mini Banking API
 * @version 1.0
 */
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /**
     * Paginated transaction history for a given account (source or target).
     * Ordered by {@code createdAt DESC} so the latest transactions appear first.
     *
     * @param accountId the account UUID
     * @param pageable  pagination + sort parameters
     * @return a page of transactions
     */
    @Query(
        value = """
            SELECT t FROM Transaction t
            LEFT JOIN FETCH t.sourceAccount
            LEFT JOIN FETCH t.targetAccount
            WHERE t.sourceAccount.id = :accountId OR t.targetAccount.id = :accountId
            ORDER BY t.createdAt DESC
            """,
        countQuery = """
            SELECT COUNT(t) FROM Transaction t
            WHERE t.sourceAccount.id = :accountId OR t.targetAccount.id = :accountId
            """
    )
    Page<Transaction> findByAccountId(@Param("accountId") UUID accountId, Pageable pageable);

    /**
     * Sum of COMPLETED outbound transfer amounts from a source account within a time window.
     * Used to enforce daily/monthly transfer limits.
     *
     * @param accountId the source account UUID
     * @param from      window start (inclusive)
     * @param to        window end (inclusive)
     * @return total transferred amount, or {@link BigDecimal#ZERO} if none
     */
    @Query("""
        SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
        WHERE t.sourceAccount.id = :accountId
          AND t.status = com.banking.enums.TransactionStatus.COMPLETED
          AND t.createdAt BETWEEN :from AND :to
        """)
    BigDecimal sumCompletedTransferAmountByAccountIdAndDateRange(
        @Param("accountId") UUID accountId,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to
    );

    /**
     * Finds a transaction by its unique reference number.
     * Used for idempotency key checks and customer support lookups.
     *
     * @param referenceNumber the TXN-YYYYMMDD-UUID8 reference
     * @return the transaction or empty
     */
    Optional<Transaction> findByReferenceNumber(String referenceNumber);

    /**
     * Checks whether a transaction with this reference number already exists.
     * Implements idempotency: if the same X-Idempotency-Key maps to an existing
     * reference, we return the existing result without re-processing.
     *
     * @param referenceNumber the reference number derived from the idempotency key
     * @return {@code true} if already processed
     */
    boolean existsByReferenceNumber(String referenceNumber);
}
