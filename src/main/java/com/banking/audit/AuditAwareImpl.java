package com.banking.audit;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

/**
 * Provides the currently authenticated user's email to the Spring Data JPA
 * auditing framework, which uses it to populate {@code createdBy} and {@code updatedBy}
 * on {@link com.banking.entity.base.BaseEntity}.
 *
 * <p>Falls back to {@code "system"} for operations performed outside a request
 * context (e.g. Flyway migrations, startup initialization).
 *
 * @author Mini Banking API
 * @version 1.0
 */
public class AuditAwareImpl implements AuditorAware<String> {

    private static final String SYSTEM_USER = "system";

    /**
     * Returns the email of the currently authenticated principal, or {@code "system"}
     * if no authentication is present in the security context.
     *
     * @return an {@link Optional} containing the auditor's identifier
     */
    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
            || !authentication.isAuthenticated()
            || authentication.getPrincipal() == null
            || "anonymousUser".equals(authentication.getPrincipal())) {
            return Optional.of(SYSTEM_USER);
        }

        if (authentication.getPrincipal() instanceof UserDetails userDetails) {
            return Optional.of(userDetails.getUsername());
        }

        return Optional.of(authentication.getName());
    }
}
