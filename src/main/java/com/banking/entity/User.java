package com.banking.entity;

import com.banking.entity.base.BaseEntity;
import com.banking.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Core user domain entity implementing Spring Security's {@link UserDetails}.
 *
 * <p>Security design decisions:
 * <ul>
 *   <li>Email is normalized to lowercase on {@code @PrePersist}/{@code @PreUpdate}
 *       to prevent duplicate accounts via case-variation attacks.</li>
 *   <li>Failed login counter triggers a timed lockout after
 *       {@code app.banking.account-lockout-attempts} failures to mitigate
 *       brute-force credential stuffing.</li>
 *   <li>The {@code password} field is intentionally excluded from Lombok's
 *       {@code @ToString} to prevent accidental secret exposure in logs.</li>
 * </ul>
 *
 * @author Mini Banking API
 * @version 1.0
 */
@Entity
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_users_email", columnList = "email", unique = true),
        @Index(name = "idx_users_phone_number", columnList = "phone_number", unique = true)
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity implements UserDetails {

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    /** Stored lowercase; uniqueness enforced at DB level via unique index. */
    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    /** BCrypt-hashed with strength 12. Never exposed in DTOs or logs. */
    @Column(name = "password", nullable = false)
    private String password;

    /** E.164 format enforced by validation annotation: +998XXXXXXXXX */
    @Column(name = "phone_number", nullable = false, unique = true, length = 20)
    private String phoneNumber;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.ROLE_USER;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "account_non_locked", nullable = false)
    @Builder.Default
    private boolean accountNonLocked = true;

    /**
     * Incremented on each failed login attempt.
     * Reset to 0 on successful authentication.
     * Triggers lockout at threshold defined in application properties.
     */
    @Column(name = "failed_login_attempts", nullable = false)
    @Builder.Default
    private int failedLoginAttempts = 0;

    /**
     * When set, the account is locked until this timestamp.
     * {@code null} means no active timed lockout.
     */
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<Account> accounts = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<RefreshToken> refreshTokens = new ArrayList<>();

    // ── Lifecycle hooks ──────────────────────────────────────────────────────

    /**
     * Normalizes email to lowercase before initial persist.
     * Prevents duplicate accounts via case-variation attacks (e.g. User@Bank.com vs user@bank.com).
     */
    @PrePersist
    @PreUpdate
    private void normalizeEmail() {
        if (email != null) {
            email = email.toLowerCase().trim();
        }
    }

    // ── UserDetails implementation ───────────────────────────────────────────

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.name()));
    }

    /** Spring Security uses email as the username principal. */
    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        // Re-check timed lockout on each authentication attempt
        if (lockedUntil != null && LocalDateTime.now(ZoneOffset.UTC).isBefore(lockedUntil)) {
            return false;
        }
        return accountNonLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
