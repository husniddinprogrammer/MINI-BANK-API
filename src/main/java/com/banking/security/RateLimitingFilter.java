package com.banking.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP-based rate limiter that runs before authentication.
 *
 * <p>Enforces two tiers:
 * <ul>
 *   <li>Auth endpoints ({@code /api/v1/auth/**}): 10 requests/minute — reduces brute-force risk.</li>
 *   <li>All other endpoints: 100 requests/minute — general DoS protection.</li>
 * </ul>
 *
 * <p>X-Forwarded-For is only trusted when the direct TCP peer ({@code RemoteAddr}) is in
 * {@link #TRUSTED_PROXIES}. Clients cannot spoof their IP by setting the header themselves.
 *
 * <p><strong>Production note:</strong> Replace the in-memory {@link ConcurrentHashMap} with a
 * Redis-backed bucket store (bucket4j-redis) for multi-node deployments.
 *
 * @author Mini Banking API
 * @version 1.0
 */
@Slf4j
@Component
@Order(1)
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final int AUTH_REQUESTS_PER_MINUTE = 10;
    private static final int DEFAULT_REQUESTS_PER_MINUTE = 100;
    private static final String AUTH_PATH_PREFIX = "/api/v1/auth/";

    // Only trust X-Forwarded-For when the direct connection comes from a known proxy.
    // In production, set this to your load balancer / reverse proxy IP(s).
    private static final Set<String> TRUSTED_PROXIES = Set.of(
        "127.0.0.1",
        "::1"
    );

    private final Map<String, Bucket> authBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> defaultBuckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain chain
    ) throws ServletException, IOException {

        String ip = extractClientIp(request);
        boolean isAuthEndpoint = request.getRequestURI().startsWith(AUTH_PATH_PREFIX);

        Bucket bucket = isAuthEndpoint
            ? authBuckets.computeIfAbsent(ip, k -> createBucket(AUTH_REQUESTS_PER_MINUTE))
            : defaultBuckets.computeIfAbsent(ip, k -> createBucket(DEFAULT_REQUESTS_PER_MINUTE));

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded for IP={}, endpoint={}", ip, request.getRequestURI());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("""
                {"status":429,"error":"Too Many Requests","message":"Rate limit exceeded. Try again later."}
                """);
        }
    }

    private Bucket createBucket(int requestsPerMinute) {
        return Bucket.builder()
            .addLimit(Bandwidth.classic(
                requestsPerMinute,
                Refill.intervally(requestsPerMinute, Duration.ofMinutes(1))
            ))
            .build();
    }

    private String extractClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        // SECURITY: Only honour X-Forwarded-For when the TCP peer is a known trusted proxy.
        // If any client could set this header, they could claim any IP and bypass rate limiting.
        if (TRUSTED_PROXIES.contains(remoteAddr)) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
        }
        return remoteAddr;
    }
}
