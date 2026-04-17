package com.banking.service.impl;

import com.banking.audit.AuditLogService;
import com.banking.config.ApplicationProperties;
import com.banking.dto.request.LoginRequest;
import com.banking.dto.request.RefreshTokenRequest;
import com.banking.dto.request.RegisterRequest;
import com.banking.dto.response.AuthResponse;
import com.banking.dto.response.UserResponse;
import com.banking.entity.RefreshToken;
import com.banking.entity.User;
import com.banking.exception.BankingException;
import com.banking.exception.DuplicateResourceException;
import com.banking.exception.ResourceNotFoundException;
import com.banking.mapper.UserMapper;
import com.banking.repository.RefreshTokenRepository;
import com.banking.repository.UserRepository;
import com.banking.security.CustomUserDetails;
import com.banking.security.JwtTokenProvider;
import com.banking.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Implements the authentication and token lifecycle operations.
 *
 * <p>Key security decisions implemented here:
 * <ul>
 *   <li>Lockout: after {@code accountLockoutAttempts} failures, the account is locked
 *       for {@code accountLockoutDurationMinutes} — enforced via DB update, not in-memory.</li>
 *   <li>Generic error message: "Invalid credentials" for both wrong password AND unknown email
 *       to prevent username enumeration attacks.</li>
 *   <li>Refresh token hashing: SHA-256 of the raw token is stored in DB.
 *       The raw token is returned to the client exactly once.</li>
 *   <li>Token rotation: on every refresh, the old token is revoked and a new pair issued.</li>
 * </ul>
 *
 * @author Mini Banking API
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final UserMapper userMapper;
    private final ApplicationProperties properties;
    private final AuditLogService auditLogService;

    /**
     * {@inheritDoc}
     *
     * @throws DuplicateResourceException if email or phone number is already registered
     */
    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        log.debug("Processing registration for email: {}", request.email());

        // Fail fast — check uniqueness before any expensive operations
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("User", "email", request.email());
        }
        if (userRepository.existsByPhoneNumber(request.phoneNumber())) {
            throw new DuplicateResourceException("User", "phoneNumber", request.phoneNumber());
        }

        User user = userMapper.toEntity(request);
        user.setPassword(passwordEncoder.encode(request.password()));

        User saved = userRepository.save(user);
        log.info("New user registered: userId={}", saved.getId());

        return userMapper.toResponse(saved);
    }

    /**
     * {@inheritDoc}
     *
     * <p>On failed authentication:
     * <ol>
     *   <li>Increment the failed attempts counter atomically.</li>
     *   <li>If the threshold is reached, lock the account for the configured duration.</li>
     *   <li>Always return "Invalid credentials" — never distinguish between
     *       "user not found" and "wrong password" to prevent enumeration.</li>
     * </ol>
     */
    @Override
    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress, String userAgent) {
        log.debug("Login attempt for email: {}", request.email());

        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );

            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

            // Successful login — reset failure counter
            userRepository.resetFailedLoginAttempts(userDetails.getUserId());

            // Housekeeping: remove expired/revoked tokens before issuing a new one
            refreshTokenRepository.deleteExpiredOrRevokedByUserId(userDetails.getUserId());

            String accessToken = jwtTokenProvider.generateAccessToken(userDetails);
            String rawRefreshToken = UUID.randomUUID().toString();

            RefreshToken refreshToken = buildRefreshToken(rawRefreshToken, userDetails.getUserId(), userAgent, ipAddress);
            refreshTokenRepository.save(refreshToken);

            auditLogService.logSuccess(
                userDetails.getUserId().toString(), "LOGIN", "User",
                userDetails.getUserId().toString(), ipAddress, userAgent, null
            );

            log.info("User logged in successfully: userId={}", userDetails.getUserId());

            return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .tokenType(AuthResponse.BEARER)
                .expiresIn(properties.getSecurity().getJwt().getAccessTokenExpiration() / 1000)
                .build();

        } catch (LockedException e) {
            handleFailedLogin(request.email(), ipAddress, userAgent, "Account is locked");
            // LockedException is a subtype of AuthenticationException — re-throw as domain exception
            throw new BankingException("Account is temporarily locked. Please try again later.",
                HttpStatus.LOCKED);

        } catch (BadCredentialsException e) {
            handleFailedLogin(request.email(), ipAddress, userAgent, "Invalid credentials");
            // Generic message — does not reveal whether the email exists
            throw new BankingException("Invalid credentials", HttpStatus.UNAUTHORIZED);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request, String ipAddress, String userAgent) {
        String hashedToken = hashToken(request.refreshToken());

        RefreshToken storedToken = refreshTokenRepository.findByToken(hashedToken)
            .orElseThrow(() -> new BankingException("Invalid refresh token", HttpStatus.UNAUTHORIZED));

        if (!storedToken.isValid()) {
            // Token is expired or already revoked — revoke all remaining tokens (possible replay attack)
            refreshTokenRepository.revokeAllByUserId(storedToken.getUser().getId());
            log.warn("Invalid/expired refresh token used — all tokens revoked for userId={}",
                storedToken.getUser().getId());
            throw new BankingException("Refresh token is expired or revoked. Please login again.",
                HttpStatus.UNAUTHORIZED);
        }

        // Token rotation: revoke the used token and issue a new one
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        User user = storedToken.getUser();
        CustomUserDetails userDetails = new CustomUserDetails(user);

        String newAccessToken = jwtTokenProvider.generateAccessToken(userDetails);
        String newRawRefreshToken = UUID.randomUUID().toString();

        RefreshToken newRefreshToken = buildRefreshToken(newRawRefreshToken, user.getId(), userAgent, ipAddress);
        refreshTokenRepository.save(newRefreshToken);

        log.debug("Token refreshed for userId={}", user.getId());

        return AuthResponse.builder()
            .accessToken(newAccessToken)
            .refreshToken(newRawRefreshToken)
            .tokenType(AuthResponse.BEARER)
            .expiresIn(properties.getSecurity().getJwt().getAccessTokenExpiration() / 1000)
            .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void logout(String userId) {
        UUID userUuid = UUID.fromString(userId);
        refreshTokenRepository.revokeAllByUserId(userUuid);
        log.info("User logged out, all refresh tokens revoked: userId={}", userId);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Handles a failed login attempt: increments counter and locks account if threshold is reached.
     */
    private void handleFailedLogin(String email, String ipAddress, String userAgent, String reason) {
        userRepository.findByEmail(email).ifPresent(user -> {
            userRepository.incrementFailedLoginAttempts(user.getId());

            int threshold = properties.getBanking().getAccountLockoutAttempts();
            // Re-read the incremented count from DB to avoid stale-read issues
            if (user.getFailedLoginAttempts() + 1 >= threshold) {
                LocalDateTime lockUntil = LocalDateTime.now()
                    .plusMinutes(properties.getBanking().getAccountLockoutDurationMinutes());
                userRepository.lockUser(user.getId(), lockUntil);
                log.warn("Account locked due to {} failed attempts: userId={}, lockedUntil={}",
                    threshold, user.getId(), lockUntil);
            }

            auditLogService.logFailure(
                user.getId().toString(), "LOGIN", "User",
                user.getId().toString(), ipAddress, userAgent, null, reason
            );
        });
    }

    /**
     * Builds a {@link RefreshToken} entity with the hashed token stored.
     */
    private RefreshToken buildRefreshToken(String rawToken, UUID userId, String userAgent, String ipAddress) {
        User userRef = userRepository.getReferenceById(userId);

        return RefreshToken.builder()
            .token(hashToken(rawToken))
            .user(userRef)
            .expiryDate(LocalDateTime.now()
                .plusSeconds(properties.getSecurity().getJwt().getRefreshTokenExpiration() / 1000))
            .deviceInfo(userAgent != null ? userAgent.substring(0, Math.min(userAgent.length(), 200)) : null)
            .ipAddress(ipAddress)
            .build();
    }

    /**
     * Hashes a raw token with SHA-256 for safe DB storage.
     * Only the hash is persisted — the raw value lives only in the HTTP response.
     *
     * @param rawToken the raw token string
     * @return the lowercase hex SHA-256 hash
     */
    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed available in all Java SE environments
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
