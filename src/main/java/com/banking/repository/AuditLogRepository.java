package com.banking.repository;

import com.banking.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Data access layer for {@link AuditLog} entities.
 *
 * @author Mini Banking API
 * @version 1.0
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    /**
     * Paginated audit log for a specific user, newest first.
     * Used by admin endpoints to review user activity.
     *
     * @param userId   the user's UUID as string
     * @param pageable pagination parameters
     * @return page of audit entries
     */
    @Query("SELECT a FROM AuditLog a WHERE a.userId = :userId ORDER BY a.timestamp DESC")
    Page<AuditLog> findByUserId(@Param("userId") String userId, Pageable pageable);

    /**
     * Counts failed login attempts for a user within a time window.
     * Used for anomaly detection and rate-limiting dashboards.
     *
     * @param userId the user's UUID as string
     * @param from   window start
     * @param to     window end
     * @return count of FAILURE LOGIN events
     */
    @Query("""
        SELECT COUNT(a) FROM AuditLog a
        WHERE a.userId = :userId
          AND a.action = 'LOGIN'
          AND a.result = 'FAILURE'
          AND a.timestamp BETWEEN :from AND :to
        """)
    long countFailedLoginsInWindow(
        @Param("userId") String userId,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to
    );
}
