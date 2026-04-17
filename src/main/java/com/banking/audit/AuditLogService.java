package com.banking.audit;

import com.banking.entity.AuditLog;
import com.banking.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Asynchronous audit trail writer.
 *
 * <p>{@code @Async} ensures that writing the audit record does NOT block the
 * main business transaction thread. If the async write fails, it is logged at
 * ERROR level but does NOT roll back the business operation — audit is
 * observational, not transactional.
 *
 * <p>{@code Propagation.REQUIRES_NEW} creates a new database transaction for
 * the audit write. This isolates it from the calling transaction — if the caller
 * rolls back (e.g. transfer fails), the audit record of the FAILURE is still persisted.
 *
 * @author Mini Banking API
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final String SUCCESS = "SUCCESS";
    private static final String FAILURE = "FAILURE";

    private final AuditLogRepository auditLogRepository;

    /**
     * Records a successful auditable event asynchronously.
     *
     * @param userId        the acting user's UUID string (may be null for unauthenticated events)
     * @param action        the event name (e.g. "LOGIN", "TRANSFER", "ACCOUNT_CREATED")
     * @param entityType    the domain type affected (e.g. "Account")
     * @param entityId      the UUID of the affected entity
     * @param ipAddress     the client IP (extracted from the request)
     * @param userAgent     the client User-Agent header
     * @param requestDetails sanitized JSON of relevant request parameters
     */
    @Async("auditExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSuccess(
        String userId,
        String action,
        String entityType,
        String entityId,
        String ipAddress,
        String userAgent,
        String requestDetails
    ) {
        persist(userId, action, entityType, entityId, ipAddress, userAgent, requestDetails, SUCCESS, null);
    }

    /**
     * Records a failed auditable event asynchronously.
     *
     * @param userId         the acting user's UUID string
     * @param action         the event name
     * @param entityType     the domain type affected
     * @param entityId       the UUID of the affected entity (may be null if creation failed)
     * @param ipAddress      the client IP
     * @param userAgent      the client User-Agent
     * @param requestDetails sanitized JSON of relevant parameters
     * @param failureReason  human-readable failure description (safe to log; no secrets)
     */
    @Async("auditExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFailure(
        String userId,
        String action,
        String entityType,
        String entityId,
        String ipAddress,
        String userAgent,
        String requestDetails,
        String failureReason
    ) {
        persist(userId, action, entityType, entityId, ipAddress, userAgent, requestDetails, FAILURE, failureReason);
    }

    /**
     * Internal method to build and save the {@link AuditLog} record.
     * Catches all exceptions to ensure audit failures are never surfaced to the caller.
     */
    private void persist(
        String userId,
        String action,
        String entityType,
        String entityId,
        String ipAddress,
        String userAgent,
        String requestDetails,
        String result,
        String failureReason
    ) {
        try {
            AuditLog auditLog = AuditLog.builder()
                .userId(userId)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .ipAddress(ipAddress)
                .userAgent(truncate(userAgent, 200))
                .requestDetails(requestDetails)
                .result(result)
                .failureReason(failureReason)
                .timestamp(LocalDateTime.now())
                .build();

            auditLogRepository.save(auditLog);
            log.debug("Audit log written: action={}, userId={}, result={}", action, userId, result);

        } catch (Exception e) {
            // Never propagate audit failures to the business layer
            log.error("Failed to write audit log: action={}, userId={}, error={}", action, userId, e.getMessage(), e);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
