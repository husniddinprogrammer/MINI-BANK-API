package com.banking.security;

import com.banking.config.ApplicationProperties;
import com.banking.entity.User;
import com.banking.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtTokenProvider")
class JwtTokenProviderTest {

    private static final String TEST_SECRET =
        "dGVzdC1zZWNyZXQta2V5LW11c3QtYmUtYXQtbGVhc3QtNTEyLWJpdHMtbG9uZy1mb3ItaHM1MTItYWxnb3JpdGhtLXBhZGRpbmc=";
    private static final String ISSUER = "mini-banking-api";
    private static final String AUDIENCE = "mini-banking-app";

    @Mock
    private ApplicationProperties properties;

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        ApplicationProperties.Security.Jwt jwtProps = new ApplicationProperties.Security.Jwt();
        jwtProps.setSecret(TEST_SECRET);
        jwtProps.setAccessTokenExpiration(900_000L);
        jwtProps.setRefreshTokenExpiration(604_800_000L);
        jwtProps.setIssuer(ISSUER);
        jwtProps.setAudience(AUDIENCE);

        ApplicationProperties.Security security = new ApplicationProperties.Security();
        ReflectionTestUtils.setField(security, "jwt", jwtProps);

        given(properties.getSecurity()).willReturn(security);

        jwtTokenProvider = new JwtTokenProvider(properties);
        ReflectionTestUtils.invokeMethod(jwtTokenProvider, "initSigningKey");
    }

    @Test
    @DisplayName("generated token contains iss and aud claims")
    void generatedTokenContainsIssuerAndAudience() {
        String token = jwtTokenProvider.generateAccessToken(buildAuthentication());

        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));
        Claims claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();

        assertThat(claims.getIssuer()).isEqualTo(ISSUER);
        assertThat(claims.getAudience()).contains(AUDIENCE);
    }

    @Test
    @DisplayName("validateToken returns true for valid token with correct iss and aud")
    void validateTokenReturnsTrueForValidToken() {
        String token = jwtTokenProvider.generateAccessToken(buildAuthentication());

        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("validateToken returns false when token has wrong issuer")
    void validateTokenReturnsFalseForWrongIssuer() {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));
        String token = Jwts.builder()
            .subject("user@test.com")
            .issuer("wrong-issuer")
            .audience().add(AUDIENCE).and()
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 900_000L))
            .signWith(key)
            .compact();

        assertThat(jwtTokenProvider.validateToken(token)).isFalse();
    }

    @Test
    @DisplayName("validateToken returns false when token is missing iss claim")
    void validateTokenReturnsFalseForMissingIssuer() {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));
        String token = Jwts.builder()
            .subject("user@test.com")
            .audience().add(AUDIENCE).and()
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 900_000L))
            .signWith(key)
            .compact();

        assertThat(jwtTokenProvider.validateToken(token)).isFalse();
    }

    @Test
    @DisplayName("validateToken returns false when token has wrong audience")
    void validateTokenReturnsFalseForWrongAudience() {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));
        String token = Jwts.builder()
            .subject("user@test.com")
            .issuer(ISSUER)
            .audience().add("wrong-audience").and()
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 900_000L))
            .signWith(key)
            .compact();

        assertThat(jwtTokenProvider.validateToken(token)).isFalse();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Authentication buildAuthentication() {
        User user = User.builder()
            .email("test@example.com")
            .password("hashed-password")
            .role(Role.ROLE_USER)
            .build();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());

        CustomUserDetails userDetails = new CustomUserDetails(user);
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }
}
