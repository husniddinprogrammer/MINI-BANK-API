package com.banking.entity.base;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Abstract base entity providing audit metadata for all persistent domain objects.
 *
 * <p>Uses Spring Data JPA auditing (enabled via {@code @EnableJpaAuditing} in
 * {@code AuditConfig}) to populate {@code createdAt}, {@code updatedAt},
 * {@code createdBy}, and {@code updatedBy} automatically.
 *
 * <p>{@code id} uses {@code GenerationType.UUID} (JPA 3.1 / Hibernate 6) which
 * generates a random UUID at the Java level before the INSERT, making the ID
 * available immediately without a DB round-trip.
 *
 * <p>{@code equals} and {@code hashCode} are based solely on {@code id} to ensure
 * consistent behaviour in JPA sets and maps across managed/detached entity states.
 *
 * @author Mini Banking API
 * @version 1.0
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Timestamp set at INSERT time; never updated afterwards. */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Timestamp refreshed on every UPDATE. */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Email / username of the principal who created this record. */
    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;

    /** Email / username of the principal who last modified this record. */
    @LastModifiedBy
    @Column(name = "updated_by")
    private String updatedBy;

    // ── Equality ────────────────────────────────────────────────────────────

    /**
     * Two entities are equal if and only if their {@code id} values are equal.
     * A null id (transient entity) is never equal to anything, including itself,
     * which is the safest contract for unsaved entities in collections.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BaseEntity other)) return false;
        return id != null && id.equals(other.id);
    }

    /**
     * Constant hash code for all instances that have a null id (transient).
     * After persist the id is stable, so the entity can safely enter hash-based
     * collections at that point.
     */
    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    /**
     * Minimal toString to avoid triggering lazy-loaded associations.
     *
     * @return simple class + id string
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[id=" + id + "]";
    }
}
