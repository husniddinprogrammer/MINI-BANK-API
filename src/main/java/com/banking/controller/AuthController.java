package com.banking.controller;

import com.banking.dto.request.LoginRequest;
import com.banking.dto.request.RefreshTokenRequest;
import com.banking.dto.request.RegisterRequest;
import com.banking.dto.response.ApiResponse;
import com.banking.dto.response.AuthResponse;
import com.banking.dto.response.UserResponse;
import com.banking.security.SecurityUtils;
import com.banking.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Handles user authentication and JWT token lifecycle.
 *
 * <p>All endpoints except {@code /logout} are public (no JWT required).
 * The {@code /logout} endpoint requires a valid JWT to identify which user's
 * tokens to revoke.
 *
 * @author Mini Banking API
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register, login, token refresh, and logout")
public class AuthController {

    private final AuthService authService;

    /**
     * Registers a new user account.
     *
     * @param request the registration payload (validated)
     * @return 201 Created with the new user's public profile
     */
    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public ResponseEntity<ApiResponse<UserResponse>> register(
        @Valid @RequestBody RegisterRequest request
    ) {
        UserResponse user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Registration successful", user));
    }

    /**
     * Authenticates credentials and returns a JWT token pair.
     *
     * @param request    login credentials
     * @param httpRequest for extracting client IP and User-Agent
     * @return 200 OK with access + refresh tokens
     */
    @PostMapping("/login")
    @Operation(summary = "Login and receive JWT tokens")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest httpRequest
    ) {
        AuthResponse authResponse = authService.login(
            request,
            extractIp(httpRequest),
            httpRequest.getHeader("User-Agent")
        );
        return ResponseEntity.ok(ApiResponse.success("Login successful", authResponse));
    }

    /**
     * Exchanges a refresh token for a new access token + refresh token pair.
     *
     * @param request    contains the raw refresh token
     * @param httpRequest for extracting client context
     * @return 200 OK with new token pair
     */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh JWT tokens using a valid refresh token")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
        @Valid @RequestBody RefreshTokenRequest request,
        HttpServletRequest httpRequest
    ) {
        AuthResponse authResponse = authService.refresh(
            request,
            extractIp(httpRequest),
            httpRequest.getHeader("User-Agent")
        );
        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", authResponse));
    }

    /**
     * Revokes all refresh tokens for the authenticated user (logout).
     *
     * @return 200 OK with confirmation message
     */
    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Logout and revoke all refresh tokens")
    public ResponseEntity<ApiResponse<Void>> logout() {
        String userId = SecurityUtils.getCurrentUserId().toString();
        authService.logout(userId);
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully"));
    }

    /**
     * Extracts the real client IP, accounting for reverse proxy forwarding headers.
     */
    private String extractIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            // X-Forwarded-For may contain a chain: "client, proxy1, proxy2" — take the first
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
