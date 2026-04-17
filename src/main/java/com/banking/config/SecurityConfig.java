package com.banking.config;

import com.banking.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Spring Security 6 configuration using the lambda DSL.
 *
 * <p>Security posture:
 * <ul>
 *   <li>Stateless JWT — no HTTP session, no CSRF token needed (session fixation n/a)</li>
 *   <li>BCrypt strength 12 — slower key derivation raises cost for offline attacks</li>
 *   <li>Method-level security via {@code @EnableMethodSecurity} allows
 *       {@code @PreAuthorize} on service and controller methods</li>
 *   <li>Security headers: HSTS, X-Frame-Options, Content-Type-Options, Referrer-Policy</li>
 *   <li>Actuator endpoints restricted to ADMIN role</li>
 * </ul>
 *
 * @author Mini Banking API
 * @version 1.0
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsService userDetailsService;

    /** Public endpoints that bypass JWT validation entirely. */
    private static final String[] PUBLIC_URLS = {
        "/api/v1/auth/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/v3/api-docs/**",
        "/actuator/health",
        "/actuator/info"
    };

    /**
     * Main security filter chain.
     *
     * <p>CSRF is disabled because the API is stateless (JWT bearer tokens are not
     * susceptible to CSRF — CSRF exploits rely on the browser automatically sending
     * session cookies, which this API does not use).
     *
     * @param http the {@link HttpSecurity} builder
     * @return configured {@link SecurityFilterChain}
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            // CSRF disabled: stateless JWT; no session cookies that CSRF could exploit
            .csrf(AbstractHttpConfigurer::disable)

            // Enforce HTTPS, deny framing, strict content type handling
            .headers(headers -> headers
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000)
                )
                .frameOptions(frame -> frame.deny())
                .contentTypeOptions(content -> {})
                .referrerPolicy(referrer ->
                    referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                )
            )

            // Stateless — never create or use HTTP sessions
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Authorization rules — ordered from most specific to least specific
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_URLS).permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )

            // Wire the JWT filter before Spring Security's own username/password filter
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

            .build();
    }

    /**
     * Builds a {@link DaoAuthenticationProvider} wired with our UserDetailsService and encoder.
     *
     * <p>Intentionally NOT a {@code @Bean} — exposing it as a Spring bean alongside the
     * {@code AuthenticationManager} bean causes Spring Security to warn that the
     * UserDetailsService will be bypassed. Calling it as a plain method keeps the
     * provider private to this config while still wiring it into the filter chain.
     *
     * @return configured authentication provider
     */
    private AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * Exposes the {@link AuthenticationManager} so {@link com.banking.service.impl.AuthServiceImpl}
     * can programmatically authenticate credentials.
     *
     * @param config Spring's authentication configuration
     * @return the authentication manager
     * @throws Exception if the manager cannot be obtained
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * BCrypt with strength 12 (default is 10).
     * Strength 12 produces ~300ms hashing time, making brute-force attacks
     * ~4× more expensive than the default strength 10.
     *
     * @return the password encoder bean
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
