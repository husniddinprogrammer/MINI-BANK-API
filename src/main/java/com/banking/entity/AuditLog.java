package com.banking.entity;

import com.banking.entity.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Immutable audit trail record written asynchronously after every significant
 * security and business event.
 *
 * <p>Sensitive fields (passwords, full account numbers, full tokens) are NEVER
 * stored. The {@code requestDetails} JSON is pre-sanitized by the caller
 * (masking applied before passing to {@link com.banking.audit.AuditLogService}).
 *
 * <p>Written via {@code @Async} so the audit trail write never blocks the main
 * business transaction. If the async write fails, it is logged at ERROR level
 * but does NOT roll back the business operation — audit is observational, not transactional.
 *
 * @author Mini Banking API
 * @version 1.0
 */
@Entity
@Table(
    name = "audit_logs",
    indexes = {
        @Index(name = "idx_audit_logs_user_id_created_at", columnList = "user_id, created_at"),
        @Index(name = "idx_audit_logs_action", columnList = "action"),
        @Index(name = "idx_audit_logs_timestamp", columnList = "timestamp")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog extends BaseEntity {

    @Column(name = "user_id", length = 36)
    private String userId;

    /** Coarse-grained event name: LOGIN, LOGOUT, TRANSFER, ACCOUNT_CREATED, etc. */
    @Column(name = "action", nullable = false, length = 50)
    private String action;

    /** Domain class name of the affected entity, e.g. "Account", "Transaction". */
    @Column(name = "entity_type", length = 50)
    private String entityType;

    /** UUID of the affected entity as a string. */
    @Column(name = "entity_id", length = 36)
    private String entityId;

    /** Client IP address extracted from the request (masked if needed). */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /** JSON blob of sanitized request parameters — no raw passwords or tokens. */
    @Column(name = "request_details", columnDefinition = "TEXT")
    private String requestDetails;

    /** SUCCESS or FAILURE. */
    @Column(name = "result", nullable = false, length = 10)
    private String result;

    /** Populated on FAILURE — human-readable reason for logging/review. */
    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
}
