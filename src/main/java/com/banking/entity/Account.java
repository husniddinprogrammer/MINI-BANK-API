package com.banking.entity;

import com.banking.entity.base.BaseEntity;
import com.banking.enums.AccountStatus;
import com.banking.enums.AccountType;
import com.banking.enums.Currency;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a bank account owned by a {@link User}.
 *
 * <p>Key design decisions:
 * <ul>
 *   <li>{@code @Version} enables optimistic locking via Hibernate — concurrent
 *       updates increment the version column, causing {@code OptimisticLockException}
 *       if two transactions read the same version. This is the last line of defense
 *       against double-spend races; PESSIMISTIC_WRITE locks in the service layer
 *       provide the first line.</li>
 *   <li>{@code balance} uses {@code precision=19, scale=4} — the monetary standard
 *       for 15 integer digits + 4 decimal places (BigDecimal arithmetic is exact).</li>
 *   <li>{@code accountNumber} is generated in the service layer (not here) to allow
 *       retry on collision without coupling generation logic to the entity lifecycle.</li>
 * </ul>
 *
 * @author Mini Banking API
 * @version 1.0
 */
@Entity
@Table(
    name = "accounts",
    indexes = {
        @Index(name = "idx_accounts_account_number", columnList = "account_number", unique = true),
        @Index(name = "idx_accounts_owner_id", columnList = "owner_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account extends BaseEntity {

    /** 16-digit number, format: 8600XXXXXXXXXXXX. Generated in service layer. */
    @Column(name = "account_number", nullable = false, unique = true, length = 20)
    private String accountNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private AccountStatus status = AccountStatus.ACTIVE;

    /** Current balance; monetary precision 19,4 per banking standard. */
    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 10)
    @Builder.Default
    private Currency currency = Currency.UZS;

    @Column(name = "daily_transfer_limit", precision = 19, scale = 4)
    private BigDecimal dailyTransferLimit;

    @Column(name = "monthly_transfer_limit", precision = 19, scale = 4)
    private BigDecimal monthlyTransferLimit;

    /** Only one primary account is allowed per user; enforced in service layer. */
    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private boolean isPrimary = false;

    @OneToMany(mappedBy = "sourceAccount", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Transaction> sentTransactions = new ArrayList<>();

    @OneToMany(mappedBy = "targetAccount", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Transaction> receivedTransactions = new ArrayList<>();

    /**
     * Optimistic lock version column.
     * Hibernate increments this on every UPDATE; stale reads throw OptimisticLockException.
     * Combined with PESSIMISTIC_WRITE in transfers, this provides defense-in-depth
     * against concurrent balance modifications.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
