package com.banking.integration;

import com.banking.dto.request.TransferRequest;
import com.banking.dto.response.TransactionResponse;
import com.banking.entity.Account;
import com.banking.entity.User;
import com.banking.enums.AccountStatus;
import com.banking.enums.AccountType;
import com.banking.enums.Currency;
import com.banking.enums.Role;
import com.banking.repository.AccountRepository;
import com.banking.repository.TransactionRepository;
import com.banking.repository.UserRepository;
import com.banking.service.TransactionService;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private TransactionService transactionService;
    @Autowired private PasswordEncoder passwordEncoder;

    private UUID userAId;
    private UUID userBId;

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
        userAId = userA.getId();

        User userB = userRepository.save(User.builder()
            .firstName("Bob").lastName("Jones")
            .email("bob@test.com")
            .password(passwordEncoder.encode("Password@123"))
            .phoneNumber("+998902222222")
            .role(Role.ROLE_USER)
            .build());
        userBId = userB.getId();

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

    @Test
    @DisplayName("Duplicate idempotency key returns original result without debiting twice")
    void shouldDeduplicateTransferWithSameIdempotencyKey() {
        String idempotencyKey = UUID.randomUUID().toString();
        TransferRequest request = new TransferRequest(
            accountA.getId(), accountB.getId(), new BigDecimal("1000.0000"), "idempotency test"
        );

        TransactionResponse first  = transactionService.transfer(request, userAId, idempotencyKey, "127.0.0.1", "test-agent");
        TransactionResponse second = transactionService.transfer(request, userAId, idempotencyKey, "127.0.0.1", "test-agent");

        // Second call must return the same transaction record — not a new one
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.referenceNumber()).isEqualTo(first.referenceNumber());

        // Balance was deducted exactly once
        Account updatedA = accountRepository.findById(accountA.getId()).orElseThrow();
        assertThat(updatedA.getBalance()).isEqualByComparingTo("99000.0000");

        // Only one transaction record exists for this idempotency key (stored as reference number)
        long txCount = transactionRepository.findAll().stream()
            .filter(tx -> ("TXN-IDMP-" + idempotencyKey).equals(tx.getReferenceNumber()))
            .count();
        assertThat(txCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Concurrent opposite-direction transfers complete without deadlock")
    void shouldHandleConcurrentReverseTransfersWithoutDeadlock() throws Exception {
        TransferRequest aToB = new TransferRequest(
            accountA.getId(), accountB.getId(), new BigDecimal("10000.0000"), "A to B"
        );
        TransferRequest bToA = new TransferRequest(
            accountB.getId(), accountA.getId(), new BigDecimal("10000.0000"), "B to A"
        );

        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Both transfers start at the same instant to maximise deadlock probability
        List<Future<TransactionResponse>> futures = List.of(
            executor.submit((Callable<TransactionResponse>) () -> {
                startLatch.await();
                return transactionService.transfer(aToB, userAId, UUID.randomUUID().toString(), "127.0.0.1", "agent-a");
            }),
            executor.submit((Callable<TransactionResponse>) () -> {
                startLatch.await();
                return transactionService.transfer(bToA, userBId, UUID.randomUUID().toString(), "127.0.0.1", "agent-b");
            })
        );

        startLatch.countDown();
        for (Future<TransactionResponse> f : futures) {
            f.get(15, TimeUnit.SECONDS); // deadlock would time out here
        }
        executor.shutdown();

        // Net effect: each account transferred the same amount in both directions → balances unchanged
        Account finalA = accountRepository.findById(accountA.getId()).orElseThrow();
        Account finalB = accountRepository.findById(accountB.getId()).orElseThrow();
        assertThat(finalA.getBalance()).isEqualByComparingTo("100000.0000");
        assertThat(finalB.getBalance()).isEqualByComparingTo("100000.0000");
    }
}
