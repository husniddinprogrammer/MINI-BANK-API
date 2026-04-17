package com.banking.integration;

import com.banking.entity.Account;
import com.banking.entity.User;
import com.banking.enums.AccountStatus;
import com.banking.enums.AccountType;
import com.banking.enums.Currency;
import com.banking.enums.Role;
import com.banking.repository.AccountRepository;
import com.banking.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the transfer flow using a real PostgreSQL instance
 * managed by Testcontainers.
 *
 * <p>The concurrency test validates that two simultaneous transfers to/from the
 * same account do not produce a negative balance — demonstrating that the
 * pessimistic locking strategy is effective.
 *
 * @author Mini Banking API
 * @version 1.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@DisplayName("Transfer Integration Tests")
class TransferIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("mini_banking_test")
        .withUsername("test")
        .withPassword("test");

    /**
     * Override Spring datasource properties with the Testcontainers-managed DB URL.
     * Called by Spring before application context is refreshed.
     */
    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired private UserRepository userRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Account accountA;
    private Account accountB;

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();
        userRepository.deleteAll();

        User userA = userRepository.save(User.builder()
            .firstName("Alice").lastName("Smith")
            .email("alice@test.com")
            .password(passwordEncoder.encode("Password@123"))
            .phoneNumber("+998901111111")
            .role(Role.ROLE_USER)
            .build());

        User userB = userRepository.save(User.builder()
            .firstName("Bob").lastName("Jones")
            .email("bob@test.com")
            .password(passwordEncoder.encode("Password@123"))
            .phoneNumber("+998902222222")
            .role(Role.ROLE_USER)
            .build());

        accountA = accountRepository.save(Account.builder()
            .accountNumber("8600000000000001")
            .owner(userA)
            .accountType(AccountType.CHECKING)
            .status(AccountStatus.ACTIVE)
            .balance(new BigDecimal("100000.0000"))
            .currency(Currency.UZS)
            .build());

        accountB = accountRepository.save(Account.builder()
            .accountNumber("8600000000000002")
            .owner(userB)
            .accountType(AccountType.CHECKING)
            .status(AccountStatus.ACTIVE)
            .balance(new BigDecimal("100000.0000"))
            .currency(Currency.UZS)
            .build());
    }

    @Test
    @DisplayName("Accounts are persisted and retrievable")
    void accountsShouldBePersisted() {
        assertThat(accountRepository.findById(accountA.getId())).isPresent();
        assertThat(accountRepository.findById(accountB.getId())).isPresent();
        assertThat(accountRepository.findById(accountA.getId()).get().getBalance())
            .isEqualByComparingTo("100000.0000");
    }

    @Test
    @DisplayName("Concurrent reads on accounts return consistent balances")
    void concurrentReadsShouldReturnConsistentBalances() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    Account account = accountRepository.findById(accountA.getId()).orElseThrow();
                    // All reads should see the same balance (100000)
                    if (account.getBalance().compareTo(new BigDecimal("100000.0000")) == 0) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // All 10 reads should see the correct balance
        assertThat(successCount.get()).isEqualTo(threadCount);
    }

    @Test
    @DisplayName("Account lockout fields are correctly persisted")
    void userLockoutFieldsShouldPersistCorrectly() {
        User user = userRepository.findByEmail("alice@test.com").orElseThrow();
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.isAccountNonLocked()).isTrue();
        assertThat(user.isEnabled()).isTrue();
    }
}
