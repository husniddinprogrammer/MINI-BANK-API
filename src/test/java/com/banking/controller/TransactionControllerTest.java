package com.banking.controller;

import com.banking.config.SecurityConfig;
import com.banking.dto.request.TransferRequest;
import com.banking.dto.response.TransactionResponse;
import com.banking.entity.User;
import com.banking.enums.Role;
import com.banking.exception.BankingException;
import com.banking.security.CustomUserDetails;
import com.banking.security.CustomUserDetailsService;
import com.banking.security.JwtTokenProvider;
import com.banking.service.TransactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
@Import(SecurityConfig.class)
@DisplayName("TransactionController — idempotency key validation")
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TransactionService transactionService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    private UsernamePasswordAuthenticationToken authToken;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        User user = User.builder()
            .email("test@example.com")
            .password("hashed")
            .role(Role.ROLE_USER)
            .build();
        ReflectionTestUtils.setField(user, "id", userId);

        CustomUserDetails userDetails = new CustomUserDetails(user);
        authToken = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    @Test
    @DisplayName("POST /transfer — 201 Created when valid UUID idempotency key is provided")
    void shouldReturn201WhenValidIdempotencyKey() throws Exception {
        TransferRequest request = new TransferRequest(
            UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1000.00"), "test");

        TransactionResponse mockTx = TransactionResponse.builder()
            .id(UUID.randomUUID())
            .referenceNumber("TXN-20240101-ABCD1234")
            .build();

        given(transactionService.transfer(any(), any(), anyString(), any(), any()))
            .willReturn(mockTx);

        mockMvc.perform(post("/api/v1/transactions/transfer")
                .with(authentication(authToken))
                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /transfer — 400 Bad Request when X-Idempotency-Key header is missing")
    void shouldReturn400WhenIdempotencyKeyMissing() throws Exception {
        TransferRequest request = new TransferRequest(
            UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1000.00"), null);

        mockMvc.perform(post("/api/v1/transactions/transfer")
                .with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /transfer — 400 Bad Request when X-Idempotency-Key is not a valid UUID")
    void shouldReturn400WhenIdempotencyKeyIsNotUuid() throws Exception {
        TransferRequest request = new TransferRequest(
            UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1000.00"), null);

        mockMvc.perform(post("/api/v1/transactions/transfer")
                .with(authentication(authToken))
                .header("X-Idempotency-Key", "not-a-uuid")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /transfer — 422 Unprocessable Entity when idempotency key conflicts with prior transfer")
    void shouldReturn422WhenIdempotencyKeyConflicts() throws Exception {
        TransferRequest request = new TransferRequest(
            UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1000.00"), null);

        given(transactionService.transfer(any(), any(), anyString(), any(), any()))
            .willThrow(new BankingException(
                "Idempotency key conflict: this key was already used for a different transfer",
                HttpStatus.UNPROCESSABLE_ENTITY));

        mockMvc.perform(post("/api/v1/transactions/transfer")
                .with(authentication(authToken))
                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnprocessableEntity());
    }
}
