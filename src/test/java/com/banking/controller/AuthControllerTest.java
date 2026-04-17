package com.banking.controller;

import com.banking.config.SecurityConfig;
import com.banking.dto.request.LoginRequest;
import com.banking.dto.request.RegisterRequest;
import com.banking.dto.response.AuthResponse;
import com.banking.dto.response.UserResponse;
import com.banking.enums.Role;
import com.banking.security.CustomUserDetailsService;
import com.banking.security.JwtTokenProvider;
import com.banking.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc controller tests for {@link AuthController}.
 *
 * <p>Tests verify HTTP status codes, response structure, and validation behaviour
 * without starting a real server.
 *
 * @author Mini Banking API
 * @version 1.0
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@DisplayName("AuthController")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    // ── POST /api/v1/auth/register ────────────────────────────────────────────

    @Test
    @DisplayName("POST /register — 201 Created on valid payload")
    void shouldReturn201OnValidRegistration() throws Exception {
        RegisterRequest request = new RegisterRequest(
            "John", "Doe", "john@bank.com", "Password@123", "+998901234567",
            LocalDate.of(1990, 1, 1));

        UserResponse mockResponse = new UserResponse(
            UUID.randomUUID(), "John", "Doe", "john@bank.com",
            "+998901234567", null, Role.ROLE_USER, true, LocalDateTime.now());

        given(authService.register(any(RegisterRequest.class))).willReturn(mockResponse);

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.email").value("john@bank.com"));
    }

    @Test
    @DisplayName("POST /register — 400 Bad Request when email is invalid")
    void shouldReturn400OnInvalidEmail() throws Exception {
        RegisterRequest request = new RegisterRequest(
            "John", "Doe", "not-an-email", "Password@123", "+998901234567", null);

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    @DisplayName("POST /register — 400 Bad Request when password is too weak")
    void shouldReturn400OnWeakPassword() throws Exception {
        RegisterRequest request = new RegisterRequest(
            "John", "Doe", "john@bank.com", "weakpassword", "+998901234567", null);

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    // ── POST /api/v1/auth/login ───────────────────────────────────────────────

    @Test
    @DisplayName("POST /login — 200 OK on valid credentials")
    void shouldReturn200OnValidLogin() throws Exception {
        LoginRequest request = new LoginRequest("john@bank.com", "Password@123");

        AuthResponse mockAuth = AuthResponse.builder()
            .accessToken("access.token.here")
            .refreshToken("refresh-token-here")
            .tokenType("Bearer")
            .expiresIn(900)
            .build();

        given(authService.login(any(LoginRequest.class), any(), any())).willReturn(mockAuth);

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken").value("access.token.here"))
            .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("POST /login — 400 Bad Request when email blank")
    void shouldReturn400OnBlankEmail() throws Exception {
        LoginRequest request = new LoginRequest("", "Password@123");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    // ── POST /api/v1/auth/logout — requires authentication ───────────────────

    @Test
    @DisplayName("POST /logout — 401 Unauthorized without JWT")
    void shouldReturn401OnLogoutWithoutJwt() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
            .andExpect(status().isUnauthorized());
    }
}
