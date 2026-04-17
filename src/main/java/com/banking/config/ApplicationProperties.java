package com.banking.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

/**
 * Strongly-typed configuration properties bound from the {@code app.*} namespace.
 *
 * <p>Using {@code @ConfigurationProperties} instead of scattered {@code @Value}
 * annotations provides:
 * <ul>
 *   <li>Compile-time refactoring safety</li>
 *   <li>IDE auto-completion via the configuration processor</li>
 *   <li>JSR-303 validation at startup (fail-fast on misconfigured environments)</li>
 * </ul>
 *
 * @author Mini Banking API
 * @version 1.0
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app")
public class ApplicationProperties {

    @NotNull
    private Security security = new Security();

    @NotNull
    private Banking banking = new Banking();

    // ── Nested: Security ────────────────────────────────────────────────────

    @Getter
    @Setter
    public static class Security {

        @NotNull
        private Jwt jwt = new Jwt();

        @Getter
        @Setter
        public static class Jwt {

            /** HMAC-SHA512 signing secret — must be ≥ 512 bits (64 bytes). */
            @NotBlank
            private String secret;

            /** Access token TTL in milliseconds (default: 900000 = 15 min). */
            @Min(60000)
            private long accessTokenExpiration = 900_000L;

            /** Refresh token TTL in milliseconds (default: 604800000 = 7 days). */
            @Min(3600000)
            private long refreshTokenExpiration = 604_800_000L;
        }
    }

    // ── Nested: Banking rules ───────────────────────────────────────────────

    @Getter
    @Setter
    public static class Banking {

        @Min(1)
        private int maxAccountsPerUser = 5;

        @NotNull
        private BigDecimal dailyTransferLimit = new BigDecimal("50000000");

        @NotNull
        private BigDecimal monthlyTransferLimit = new BigDecimal("500000000");

        @NotNull
        private BigDecimal largeWithdrawalThreshold = new BigDecimal("10000000");

        private double largeWithdrawalFeePercent = 0.5;

        @Min(1)
        private int accountLockoutAttempts = 5;

        @Min(1)
        private int accountLockoutDurationMinutes = 30;
    }
}
