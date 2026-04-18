package com.banking.service;

import com.banking.audit.AuditLogService;
import com.banking.config.ApplicationProperties;
import com.banking.dto.request.LoginRequest;
import com.banking.dto.request.RegisterRequest;
import com.banking.dto.response.AuthResponse;
import com.banking.dto.response.UserResponse;
import com.banking.entity.User;
import com.banking.enums.Role;
import com.banking.exception.BankingException;
import com.banking.exception.DuplicateResourceException;
import com.banking.mapper.UserMapper;
import com.banking.repository.RefreshTokenRepository;
import com.banking.repository.UserRepository;
import com.banking.security.CustomUserDetails;
import com.banking.security.JwtTokenProvider;
import com.banking.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * Unit tests for {@link AuthServiceImpl}.
 *
 * <p>Tests are organized by method using nested classes for clarity.
 * All external dependencies are mocked — no DB or Spring context required.
 *
 * @author Mini Banking API
 * @version 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl")
class AuthServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private UserMapper userMapper;
    @Mock private ApplicationProperties properties;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private AuthServiceImpl authService;

    private static final String TEST_EMAIL = "test@bank.com";
    private static final String TEST_PASSWORD = "Password@123";
    private static final String TEST_PHONE = "+998901234567";
    private static final String TEST_IP = "127.0.0.1";
    private static final String TEST_UA = "TestBrowser/1.0";

    private ApplicationProperties.Security security;
    private ApplicationProperties.Security.Jwt jwt;
    private ApplicationProperties.Banking banking;

    @BeforeEach
    void setUp() {
        // Wire ApplicationProperties mock tree
        security = new ApplicationProperties.Security();
        jwt = new ApplicationProperties.Security.Jwt();
        jwt.setSecret("dGVzdC1zZWNyZXQta2V5LW11c3QtYmUtYXQtbGVhc3QtNTEyLWJpdHMtbG9uZy1mb3ItaHM1MTItYWxnb3JpdGhtLXBhZGRpbmc=");
        jwt.setAccessTokenExpiration(900_000L);
        jwt.setRefreshTokenExpiration(604_800_000L);
        security.setJwt(jwt);

        banking = new ApplicationProperties.Banking();
        banking.setAccountLockoutAttempts(5);
        banking.setAccountLockoutDurationMinutes(30);

        // Stubs added only in the specific tests that need them (STRICT_STUBS requires no unused stubs).
    }

    // ── register() ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("should register a new user successfully")
        void shouldRegisterNewUser() {
            RegisterRequest request = new RegisterRequest(
                "John", "Doe", TEST_EMAIL, TEST_PASSWORD, TEST_PHONE, LocalDate.of(1990, 1, 1));

            User savedUser = User.builder()
                .firstName("John").lastName("Doe").email(TEST_EMAIL)
                .password("hashed").phoneNumber(TEST_PHONE).role(Role.ROLE_USER)
                .build();

            UserResponse expectedResponse = new UserResponse(
                UUID.randomUUID(), "John", "Doe", TEST_EMAIL, TEST_PHONE, null, Role.ROLE_USER, true, null);

            given(userRepository.existsByEmail(TEST_EMAIL)).willReturn(false);
            given(userRepository.existsByPhoneNumber(TEST_PHONE)).willReturn(false);
            given(userMapper.toEntity(request)).willReturn(savedUser);
            given(passwordEncoder.encode(TEST_PASSWORD)).willReturn("hashed");
            given(userRepository.save(any(User.class))).willReturn(savedUser);
            given(userMapper.toResponse(savedUser)).willReturn(expectedResponse);

            UserResponse result = authService.register(request);

            assertThat(result).isEqualTo(expectedResponse);
            then(passwordEncoder).should().encode(TEST_PASSWORD);
            then(userRepository).should().save(savedUser);
        }

        @Test
        @DisplayName("should throw DuplicateResourceException when email already exists")
        void shouldThrowOnDuplicateEmail() {
            RegisterRequest request = new RegisterRequest(
                "John", "Doe", TEST_EMAIL, TEST_PASSWORD, TEST_PHONE, null);

            given(userRepository.existsByEmail(TEST_EMAIL)).willReturn(true);

            assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("email");

            then(userRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("should throw DuplicateResourceException when phone already exists")
        void shouldThrowOnDuplicatePhone() {
            RegisterRequest request = new RegisterRequest(
                "John", "Doe", TEST_EMAIL, TEST_PASSWORD, TEST_PHONE, null);

            given(userRepository.existsByEmail(TEST_EMAIL)).willReturn(false);
            given(userRepository.existsByPhoneNumber(TEST_PHONE)).willReturn(true);

            assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("phoneNumber");
        }
    }

    // ── login() ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("login()")
    class Login {

        @Test
        @DisplayName("should return token pair on valid credentials")
        void shouldReturnTokensOnValidCredentials() {
            LoginRequest request = new LoginRequest(TEST_EMAIL, TEST_PASSWORD);

            UUID userId = UUID.randomUUID();
            User user = User.builder().email(TEST_EMAIL).build();
            org.springframework.test.util.ReflectionTestUtils.setField(user, "id", userId);
            CustomUserDetails userDetails = new CustomUserDetails(user);

            var auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

            given(properties.getSecurity()).willReturn(security);
            given(authenticationManager.authenticate(any())).willReturn(auth);
            given(jwtTokenProvider.generateAccessToken(userDetails)).willReturn("access.jwt.token");
            given(userRepository.getReferenceById(any())).willReturn(user);
            given(refreshTokenRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            AuthResponse result = authService.login(request, TEST_IP, TEST_UA);

            assertThat(result.accessToken()).isEqualTo("access.jwt.token");
            assertThat(result.tokenType()).isEqualTo("Bearer");
            assertThat(result.expiresIn()).isEqualTo(900L);
        }

        @Test
        @DisplayName("should throw BankingException (401) on bad credentials")
        void shouldThrowOnBadCredentials() {
            LoginRequest request = new LoginRequest(TEST_EMAIL, "WrongPassword");

            given(authenticationManager.authenticate(any()))
                .willThrow(new BadCredentialsException("Bad credentials"));
            given(userRepository.findByEmail(TEST_EMAIL)).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(request, TEST_IP, TEST_UA))
                .isInstanceOf(BankingException.class)
                .extracting("status").isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        // ── FIX-6: Timing attack prevention ──────────────────────────────────

        @Test
        @DisplayName("FIX-6: should call passwordEncoder.encode() when email does not exist — timing equalization")
        void shouldCallDummyHashWhenEmailNotFound() {
            LoginRequest request = new LoginRequest("nonexistent@bank.com", "any");

            given(authenticationManager.authenticate(any()))
                .willThrow(new BadCredentialsException("Bad credentials"));
            given(userRepository.findByEmail("nonexistent@bank.com")).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(request, TEST_IP, TEST_UA))
                .isInstanceOf(BankingException.class);

            // Dummy BCrypt ensures response time matches the existing-email path
            then(passwordEncoder).should().encode("dummy-timing-equalization-string");
        }

        @Test
        @DisplayName("FIX-6: nonexistent email path throws same exception message as wrong password path")
        void shouldThrowSameMessageWhenEmailNotFound() {
            given(authenticationManager.authenticate(any()))
                .willThrow(new BadCredentialsException("Bad credentials"));
            given(userRepository.findByEmail(TEST_EMAIL)).willReturn(Optional.empty());

            BankingException ex = catchThrowableOfType(
                () -> authService.login(new LoginRequest(TEST_EMAIL, "any"), TEST_IP, TEST_UA),
                BankingException.class);

            assertThat(ex.getMessage()).isEqualTo("Invalid credentials");
            assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        // ── FIX-5: Atomic lockout ─────────────────────────────────────────────

        @Test
        @DisplayName("FIX-5: should call atomic incrementFailedAttemptsAndLockIfThreshold on failed login")
        void shouldCallAtomicIncrementOnFailedLogin() {
            LoginRequest request = new LoginRequest(TEST_EMAIL, "WrongPassword");
            User existingUser = User.builder().email(TEST_EMAIL).build();
            org.springframework.test.util.ReflectionTestUtils.setField(
                existingUser, "id", UUID.randomUUID());

            given(properties.getBanking()).willReturn(banking);
            given(authenticationManager.authenticate(any()))
                .willThrow(new BadCredentialsException("Bad credentials"));
            given(userRepository.findByEmail(TEST_EMAIL)).willReturn(Optional.of(existingUser));

            assertThatThrownBy(() -> authService.login(request, TEST_IP, TEST_UA))
                .isInstanceOf(BankingException.class);

            // Single atomic UPDATE — not the old 3-query approach
            then(userRepository).should().incrementFailedAttemptsAndLockIfThreshold(
                eq(existingUser.getId()),
                eq(5),   // threshold from banking properties
                any()    // lockUntil timestamp
            );
            then(userRepository).should(never()).incrementFailedLoginAttempts(any());
            then(userRepository).should(never()).lockUser(any(), any());
        }
    }

    // ── logout() ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("logout()")
    class Logout {

        @Test
        @DisplayName("should revoke all refresh tokens for the user")
        void shouldRevokeAllTokens() {
            UUID userId = UUID.randomUUID();

            authService.logout(userId.toString());

            then(refreshTokenRepository).should().revokeAllByUserId(userId);
        }
    }
}
