package com.banking.security;

import com.banking.exception.UnauthorizedAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Utility class for retrieving the currently authenticated user from the security context.
 *
 * <p>Centralizing these lookups prevents scattered {@code SecurityContextHolder.getContext()}
 * calls throughout the service layer and provides a single point for null-safety checks.
 *
 * @author Mini Banking API
 * @version 1.0
 */
public final class SecurityUtils {

    private SecurityUtils() {
        // Utility class — no instantiation
    }

    /**
     * Returns the {@link CustomUserDetails} of the currently authenticated user.
     *
     * @return the authenticated user's details
     * @throws UnauthorizedAccessException if no authenticated principal exists in the context
     */
    public static CustomUserDetails getCurrentUserDetails() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
            || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            throw new UnauthorizedAccessException("No authenticated user in security context");
        }
        return (CustomUserDetails) authentication.getPrincipal();
    }

    /**
     * Returns the UUID of the currently authenticated user.
     *
     * @return the authenticated user's UUID
     * @throws UnauthorizedAccessException if no authenticated principal exists
     */
    public static UUID getCurrentUserId() {
        return getCurrentUserDetails().getUserId();
    }

    /**
     * Returns the email of the currently authenticated user.
     *
     * @return the authenticated user's email
     * @throws UnauthorizedAccessException if no authenticated principal exists
     */
    public static String getCurrentUserEmail() {
        return getCurrentUserDetails().getEmail();
    }
}
