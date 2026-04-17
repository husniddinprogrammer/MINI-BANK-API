package com.banking.service;

import com.banking.dto.request.LoginRequest;
import com.banking.dto.request.RefreshTokenRequest;
import com.banking.dto.request.RegisterRequest;
import com.banking.dto.response.AuthResponse;
import com.banking.dto.response.UserResponse;

/**
 * Contract for user authentication and token lifecycle management.
 *
 * @author Mini Banking API
 * @version 1.0
 */
public interface AuthService {

    /**
     * Registers a new user and returns their profile.
     * Fails with {@link com.banking.exception.DuplicateResourceException} if email or phone already exists.
     *
     * @param request the registration payload
     * @return the created user's public profile
     */
    UserResponse register(RegisterRequest request);

    /**
     * Authenticates credentials and issues access + refresh tokens.
     *
     * @param request    login credentials
     * @param ipAddress  client IP for audit logging
     * @param userAgent  client User-Agent for refresh token device fingerprinting
     * @return JWT access token and refresh token pair
     */
    AuthResponse login(LoginRequest request, String ipAddress, String userAgent);

    /**
     * Rotates the refresh token: revokes the old one and issues a new pair.
     *
     * @param request    contains the raw refresh token
     * @param ipAddress  client IP
     * @param userAgent  client User-Agent
     * @return new JWT access token and new refresh token
     */
    AuthResponse refresh(RefreshTokenRequest request, String ipAddress, String userAgent);

    /**
     * Revokes all refresh tokens for the currently authenticated user (logout).
     *
     * @param userId the authenticated user's UUID string
     */
    void logout(String userId);
}
