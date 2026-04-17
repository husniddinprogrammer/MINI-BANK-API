package com.banking.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT authentication filter that intercepts every request exactly once.
 *
 * <p>Processing flow:
 * <ol>
 *   <li>Extract the Bearer token from the {@code Authorization} header.</li>
 *   <li>Validate the token's signature and expiry via {@link JwtTokenProvider}.</li>
 *   <li>Load user details from DB to confirm the account is still active/unlocked.</li>
 *   <li>Populate the Spring Security context with the authenticated principal.</li>
 *   <li>Add {@code userId} and {@code requestId} to MDC for structured log correlation.</li>
 * </ol>
 *
 * <p>Security note: if token validation fails, the filter does NOT send a 401 response —
 * it simply does not populate the security context. Spring Security's
 * {@code ExceptionTranslationFilter} handles the 401 when the downstream request
 * requires authentication.
 *
 * @author Mini Banking API
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String MDC_USER_ID = "userId";
    private static final String MDC_REQUEST_ID = "requestId";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    /**
     * Performs JWT extraction, validation, and security context population.
     *
     * @param request     the incoming HTTP request
     * @param response    the HTTP response
     * @param filterChain the remaining filter chain
     * @throws ServletException if the filter chain processing fails
     * @throws IOException      if an I/O error occurs
     */
    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            String token = extractToken(request);

            if (token != null && jwtTokenProvider.validateToken(token)) {
                String email = jwtTokenProvider.extractEmail(token);
                String userId = jwtTokenProvider.extractUserId(token);

                // Only authenticate if no existing authentication is in the context
                // (prevents re-authentication on nested dispatch)
                if (SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                    // Re-check account status on every request — account may have been
                    // locked after the token was issued (within the 15-min window)
                    if (userDetails.isEnabled() && userDetails.isAccountNonLocked()) {
                        UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                            );
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);

                        // Add user context to MDC for log correlation across all layers
                        MDC.put(MDC_USER_ID, userId);
                        MDC.put(MDC_REQUEST_ID, request.getHeader("X-Request-ID") != null
                            ? request.getHeader("X-Request-ID")
                            : java.util.UUID.randomUUID().toString()
                        );
                    }
                }
            }
        } catch (Exception e) {
            // Log but do not propagate — let Spring Security reject the unauthenticated request
            log.warn("Could not set user authentication in security context: {}", e.getMessage());
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Always clear MDC to prevent context leakage across requests in thread pools
            MDC.remove(MDC_USER_ID);
            MDC.remove(MDC_REQUEST_ID);
        }
    }

    /**
     * Extracts the raw JWT string from the {@code Authorization: Bearer <token>} header.
     *
     * @param request the HTTP request
     * @return the raw token string, or {@code null} if not present or malformed
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
