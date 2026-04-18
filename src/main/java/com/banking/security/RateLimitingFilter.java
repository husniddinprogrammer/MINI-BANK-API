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
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP-based rate limiter that runs before authentication.
 *
 * <p>Enforces a maximum of 100 requests per minute per IP address using the
 * token-bucket algorithm (Bucket4j). Requests exceeding the limit receive a
 * 429 Too Many Requests response immediately, without reaching JWT validation
 * or any business logic.
 *
 * <p>Security rationale: HMAC-SHA512 computation on every request is CPU-intensive.
 * Without rate limiting, an attacker can exhaust server CPU by flooding with
 * crafted tokens. 10,000 bad tokens/sec would saturate CPU without this filter.
 *
 * <p><strong>Production note:</strong> The in-memory {@link ConcurrentHashMap} works
 * for a single instance. In a multi-node deployment, replace with a Redis-backed
 * bucket store (bucket4j-redis) to share state across all nodes.
 *
 * @author Mini Banking API
 * @version 1.0
 */
@Slf4j
@Component
@Order(1)
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final int REQUESTS_PER_MINUTE = 100;

    // IP → token bucket; entries are created lazily on first request from each IP.
    // PRODUCTION: replace with Redis-backed distributed store.
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain chain
    ) throws ServletException, IOException {

        String ip = extractClientIp(request);
        Bucket bucket = buckets.computeIfAbsent(ip, k -> createBucket());

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded for IP={}", ip);
            // SECURITY: Return 429, not 401/403 — don't reveal auth details to rate-limited clients.
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("""
                {"status":429,"error":"Too Many Requests","message":"Rate limit exceeded. Try again later."}
                """);
        }
    }

    private Bucket createBucket() {
        return Bucket.builder()
            .addLimit(Bandwidth.classic(
                REQUESTS_PER_MINUTE,
                Refill.intervally(REQUESTS_PER_MINUTE, Duration.ofMinutes(1))
            ))
            .build();
    }

    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        // SECURITY: X-Forwarded-For can be spoofed by clients.
        // In production, validate that the XFF header originates from a trusted proxy IP.
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
