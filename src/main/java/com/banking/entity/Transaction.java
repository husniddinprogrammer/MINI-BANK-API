package com.banking.entity;

import com.banking.entity.base.BaseEntity;
import com.banking.enums.Currency;
import com.banking.enums.TransactionStatus;
import com.banking.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Immutable audit record of a financial movement between accounts.
 *
 * <p>Design decisions:
 * <ul>
 *   <li>{@code sourceAccount} is nullable — deposits have no source account.</li>
 *   <li>{@code targetAccount} is nullable — withdrawals have no target account.</li>
 *   <li>Balance snapshots ({@code balanceBefore*}/{@code balanceAfter*}) are taken
 *       atomically within the transfer transaction for an irrefutable audit trail
 *       that survives future balance corrections.</li>
 *   <li>{@code referenceNumber} format: {@code TXN-YYYYMMDD-UUID8} is human-readable
 *       for customer support lookups.</li>
 *   <li>The transaction record is written in PENDING state, then updated to
 *       COMPLETED/FAILED in the same DB transaction — so no partial records exist.</li>
 * </ul>
 *
 * @author Mini Banking API
 * @version 1.0
 */
@Entity
@Table(
    name = "transactions",
    indexes = {
        @Index(name = "idx_transactions_reference_number", columnList = "reference_number", unique = true),
        @Index(name = "idx_transactions_source_account", columnList = "source_account_id"),
        @Index(name = "idx_transactions_target_account", columnList = "target_account_id"),
        @Index(name = "idx_transactions_created_at", columnList = "created_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction extends BaseEntity {

    /** Human-readable unique reference: TXN-YYYYMMDD-UUID8. */
    @Column(name = "reference_number", nullable = false, unique = true, length = 30)
    private String referenceNumber;

    /** Null for DEPOSIT transactions (funds come from outside the system). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_account_id")
    private Account sourceAccount;

    /** Null for WITHDRAWAL transactions (funds leave the system). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_account_id")
    private Account targetAccount;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;

    /** Always positive; validated at service entry point. */
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /** Fee applied to this transaction (0 for same-currency same-bank transfers). */
    @Column(name = "fee", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal fee = BigDecimal.ZERO;

    /** Source account balance captured BEFORE debit — for audit and dispute resolution. */
    @Column(name = "balance_before_source", precision = 19, scale = 4)
    private BigDecimal balanceBeforeSource;

    /** Source account balance captured AFTER debit. */
    @Column(name = "balance_after_source", precision = 19, scale = 4)
    private BigDecimal balanceAfterSource;

    /** Target account balance captured BEFORE credit. */
    @Column(name = "balance_before_target", precision = 19, scale = 4)
    private BigDecimal balanceBeforeTarget;

    /** Target account balance captured AFTER credit. */
    @Column(name = "balance_after_target", precision = 19, scale = 4)
    private BigDecimal balanceAfterTarget;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 10)
    private Currency currency;

    @Column(name = "description", length = 255)
    private String description;

    /** Populated when status transitions to FAILED; used for customer support. */
    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    /** Timestamp when the transaction was settled (status = COMPLETED or FAILED). */
    @Column(name = "processed_at")
    private LocalDateTime processedAt;
}
