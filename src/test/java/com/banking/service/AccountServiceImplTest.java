package com.banking.service;

import com.banking.audit.AuditLogService;
import com.banking.config.ApplicationProperties;
import com.banking.dto.request.CreateAccountRequest;
import com.banking.dto.response.AccountResponse;
import com.banking.entity.Account;
import com.banking.entity.User;
import com.banking.enums.AccountStatus;
import com.banking.enums.AccountType;
import com.banking.enums.Currency;
import com.banking.exception.BankingException;
import com.banking.exception.ResourceNotFoundException;
import com.banking.exception.UnauthorizedAccessException;
import com.banking.mapper.AccountMapper;
import com.banking.repository.AccountRepository;
import com.banking.repository.TransactionRepository;
import com.banking.repository.UserRepository;
import com.banking.service.impl.AccountServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * Unit tests for {@link AccountServiceImpl}.
 *
 * <p>Critical paths tested:
 * <ul>
 *   <li>Account creation: happy path, max-limit enforcement, primary promotion/demotion</li>
 *   <li>Account retrieval: by user, by ID with ownership check</li>
 *   <li>Account closure: happy path, non-zero balance guard, already-closed guard</li>
 * </ul>
 *
 * @author Mini Banking API
 * @version 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountServiceImpl")
class AccountServiceImplTest {

    @Mock private AccountRepository accountRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private UserRepository userRepository;
    @Mock private AccountMapper accountMapper;
    @Mock private ApplicationProperties properties;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private AccountServiceImpl accountService;

    private ApplicationProperties.Banking banking;

    private UUID userId;
    private UUID accountId;
    private User owner;

    @BeforeEach
    void setUp() {
        banking = new ApplicationProperties.Banking();
        banking.setMaxAccountsPerUser(5);
        banking.setDailyTransferLimit(new BigDecimal("50000000"));
        banking.setMonthlyTransferLimit(new BigDecimal("500000000"));

        userId = UUID.randomUUID();
        accountId = UUID.randomUUID();

        owner = new User();
        owner.setId(userId);
    }

    // ── createAccount ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createAccount()")
    class CreateAccount {

        @BeforeEach
        void stubProperties() {
            // lenient: some tests throw before properties is accessed (e.g. user-not-found)
            lenient().when(properties.getBanking()).thenReturn(banking);
        }

        @Test
        @DisplayName("should create account and mark it primary when user has no existing accounts")
        void shouldCreatePrimaryWhenNoPriorAccounts() {
            CreateAccountRequest request = new CreateAccountRequest(
                AccountType.SAVINGS, Currency.UZS, false);

            Account saved = buildAccount(userId, AccountStatus.ACTIVE, BigDecimal.ZERO, true);
            saved.setId(UUID.randomUUID());
            AccountResponse response = buildResponse(saved);

            given(userRepository.findById(userId)).willReturn(Optional.of(owner));
            given(accountRepository.countByOwnerIdAndStatus(userId, AccountStatus.ACTIVE)).willReturn(0L);
            given(accountRepository.existsPrimaryAccountForUser(userId)).willReturn(false);
            given(accountRepository.existsByAccountNumber(any())).willReturn(false);
            given(accountRepository.save(any())).willReturn(saved);
            given(accountMapper.toResponse(saved)).willReturn(response);

            AccountResponse result = accountService.createAccount(request, userId);

            assertThat(result).isEqualTo(response);
            then(auditLogService).should().logSuccess(
                eq(userId.toString()), eq("ACCOUNT_CREATED"), eq("Account"),
                anyString(), isNull(), isNull(), anyString());
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when user does not exist")
        void shouldThrowWhenUserNotFound() {
            CreateAccountRequest request = new CreateAccountRequest(
                AccountType.SAVINGS, Currency.UZS, false);

            given(userRepository.findById(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.createAccount(request, userId))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw BankingException when user has reached max account limit")
        void shouldThrowWhenMaxAccountsReached() {
            CreateAccountRequest request = new CreateAccountRequest(
                AccountType.CHECKING, Currency.USD, false);

            given(userRepository.findById(userId)).willReturn(Optional.of(owner));
            given(accountRepository.countByOwnerIdAndStatus(userId, AccountStatus.ACTIVE)).willReturn(5L);

            assertThatThrownBy(() -> accountService.createAccount(request, userId))
                .isInstanceOf(BankingException.class)
                .hasMessageContaining("Maximum of 5 accounts");
        }

        @Test
        @DisplayName("should demote existing primary and promote new account when isPrimary=true")
        void shouldDemoteExistingPrimaryWhenNewRequestedAsPrimary() {
            CreateAccountRequest request = new CreateAccountRequest(
                AccountType.SAVINGS, Currency.UZS, true);

            Account existingPrimary = buildAccount(userId, AccountStatus.ACTIVE, BigDecimal.ZERO, true);
            Account saved = buildAccount(userId, AccountStatus.ACTIVE, BigDecimal.ZERO, true);
            saved.setId(UUID.randomUUID());
            AccountResponse response = buildResponse(saved);

            given(userRepository.findById(userId)).willReturn(Optional.of(owner));
            given(accountRepository.countByOwnerIdAndStatus(userId, AccountStatus.ACTIVE)).willReturn(1L);
            given(accountRepository.existsPrimaryAccountForUser(userId)).willReturn(true);
            given(accountRepository.findPrimaryAccountByOwnerId(userId))
                .willReturn(Optional.of(existingPrimary));
            given(accountRepository.existsByAccountNumber(any())).willReturn(false);
            given(accountRepository.save(any())).willReturn(saved);
            given(accountMapper.toResponse(saved)).willReturn(response);

            accountService.createAccount(request, userId);

            assertThat(existingPrimary.isPrimary()).isFalse();
            then(accountRepository).should(atLeastOnce()).save(existingPrimary);
        }

        @Test
        @DisplayName("should become primary even if isPrimary=false when user has no primary yet")
        void shouldBePrimaryWhenNoPrimaryExistsRegardlessOfFlag() {
            CreateAccountRequest request = new CreateAccountRequest(
                AccountType.CHECKING, Currency.USD, false);

            ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
            Account saved = buildAccount(userId, AccountStatus.ACTIVE, BigDecimal.ZERO, true);
            saved.setId(UUID.randomUUID());

            given(userRepository.findById(userId)).willReturn(Optional.of(owner));
            given(accountRepository.countByOwnerIdAndStatus(userId, AccountStatus.ACTIVE)).willReturn(0L);
            given(accountRepository.existsPrimaryAccountForUser(userId)).willReturn(false);
            given(accountRepository.existsByAccountNumber(any())).willReturn(false);
            given(accountRepository.save(captor.capture())).willReturn(saved);
            given(accountMapper.toResponse(any())).willReturn(buildResponse(saved));

            accountService.createAccount(request, userId);

            assertThat(captor.getValue().isPrimary()).isTrue();
        }

        @Test
        @DisplayName("should retry account number generation on collision")
        void shouldRetryAccountNumberOnCollision() {
            CreateAccountRequest request = new CreateAccountRequest(
                AccountType.SAVINGS, Currency.UZS, false);

            Account saved = buildAccount(userId, AccountStatus.ACTIVE, BigDecimal.ZERO, true);
            saved.setId(UUID.randomUUID());

            given(userRepository.findById(userId)).willReturn(Optional.of(owner));
            given(accountRepository.countByOwnerIdAndStatus(userId, AccountStatus.ACTIVE)).willReturn(0L);
            given(accountRepository.existsPrimaryAccountForUser(userId)).willReturn(false);
            // First call = collision, second call = free
            given(accountRepository.existsByAccountNumber(any()))
                .willReturn(true)
                .willReturn(false);
            given(accountRepository.save(any())).willReturn(saved);
            given(accountMapper.toResponse(any())).willReturn(buildResponse(saved));

            assertThatCode(() -> accountService.createAccount(request, userId))
                .doesNotThrowAnyException();
        }
    }

    // ── getAccountsByUserId ──────────────────────────────────────────────────

    @Nested
    @DisplayName("getAccountsByUserId()")
    class GetAccountsByUserId {

        @Test
        @DisplayName("should return mapped list of accounts for user")
        void shouldReturnMappedAccounts() {
            Account a1 = buildAccount(userId, AccountStatus.ACTIVE, BigDecimal.ZERO, true);
            Account a2 = buildAccount(userId, AccountStatus.ACTIVE, BigDecimal.ZERO, false);
            AccountResponse r1 = buildResponse(a1);
            AccountResponse r2 = buildResponse(a2);

            given(accountRepository.findAllByOwnerId(userId)).willReturn(List.of(a1, a2));
            given(accountMapper.toResponse(a1)).willReturn(r1);
            given(accountMapper.toResponse(a2)).willReturn(r2);

            List<AccountResponse> result = accountService.getAccountsByUserId(userId);

            assertThat(result).containsExactly(r1, r2);
        }

        @Test
        @DisplayName("should return empty list when user has no accounts")
        void shouldReturnEmptyList() {
            given(accountRepository.findAllByOwnerId(userId)).willReturn(List.of());

            assertThat(accountService.getAccountsByUserId(userId)).isEmpty();
        }
    }

    // ── getAccountById ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAccountById()")
    class GetAccountById {

        @Test
        @DisplayName("should return account when it belongs to the requesting user")
        void shouldReturnAccountForOwner() {
            Account account = buildAccount(userId, AccountStatus.ACTIVE, BigDecimal.ZERO, false);
            AccountResponse response = buildResponse(account);

            given(accountRepository.findById(accountId)).willReturn(Optional.of(account));
            given(accountMapper.toResponse(account)).willReturn(response);

            assertThat(accountService.getAccountById(accountId, userId)).isEqualTo(response);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when account does not exist")
        void shouldThrowWhenAccountNotFound() {
            given(accountRepository.findById(accountId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.getAccountById(accountId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw UnauthorizedAccessException when account belongs to another user")
        void shouldThrowWhenNotOwner() {
            UUID otherUserId = UUID.randomUUID();
            User otherOwner = new User();
            otherOwner.setId(otherUserId);

            Account account = buildAccount(otherUserId, AccountStatus.ACTIVE, BigDecimal.ZERO, false);
            account.setOwner(otherOwner);

            given(accountRepository.findById(accountId)).willReturn(Optional.of(account));

            assertThatThrownBy(() -> accountService.getAccountById(accountId, userId))
                .isInstanceOf(UnauthorizedAccessException.class);
        }
    }

    // ── closeAccount ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("closeAccount()")
    class CloseAccount {

        @Test
        @DisplayName("should close account when balance is zero and no pending transactions")
        void shouldCloseAccountWithZeroBalance() {
            Account account = buildAccount(userId, AccountStatus.ACTIVE, BigDecimal.ZERO, false);
            account.setId(accountId);

            given(accountRepository.findById(accountId)).willReturn(Optional.of(account));
            given(transactionRepository.countPendingByAccountId(accountId)).willReturn(0L);

            accountService.closeAccount(accountId, userId);

            assertThat(account.getStatus()).isEqualTo(AccountStatus.CLOSED);
            then(accountRepository).should().save(account);
            then(auditLogService).should().logSuccess(
                eq(userId.toString()), eq("ACCOUNT_CLOSED"), eq("Account"),
                eq(accountId.toString()), isNull(), isNull(), isNull());
        }

        @Test
        @DisplayName("should throw BankingException when account is already closed")
        void shouldThrowWhenAlreadyClosed() {
            Account account = buildAccount(userId, AccountStatus.CLOSED, BigDecimal.ZERO, false);

            given(accountRepository.findById(accountId)).willReturn(Optional.of(account));

            assertThatThrownBy(() -> accountService.closeAccount(accountId, userId))
                .isInstanceOf(BankingException.class)
                .hasMessageContaining("already closed");
        }

        @Test
        @DisplayName("should throw BankingException when account has remaining balance")
        void shouldThrowWhenBalanceNonZero() {
            Account account = buildAccount(userId, AccountStatus.ACTIVE, new BigDecimal("1000.0000"), false);

            given(accountRepository.findById(accountId)).willReturn(Optional.of(account));

            assertThatThrownBy(() -> accountService.closeAccount(accountId, userId))
                .isInstanceOf(BankingException.class)
                .hasMessageContaining("remaining balance");
        }

        @Test
        @DisplayName("should throw BankingException when account has pending transactions")
        void shouldThrowWhenPendingTransactionsExist() {
            Account account = buildAccount(userId, AccountStatus.ACTIVE, BigDecimal.ZERO, false);

            given(accountRepository.findById(accountId)).willReturn(Optional.of(account));
            given(transactionRepository.countPendingByAccountId(accountId)).willReturn(1L);

            assertThatThrownBy(() -> accountService.closeAccount(accountId, userId))
                .isInstanceOf(BankingException.class)
                .hasMessageContaining("pending transactions");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when account does not exist")
        void shouldThrowWhenAccountNotFound() {
            given(accountRepository.findById(accountId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.closeAccount(accountId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw UnauthorizedAccessException when account belongs to another user")
        void shouldThrowWhenNotOwner() {
            UUID otherUserId = UUID.randomUUID();
            User otherOwner = new User();
            otherOwner.setId(otherUserId);

            Account account = buildAccount(otherUserId, AccountStatus.ACTIVE, BigDecimal.ZERO, false);
            account.setOwner(otherOwner);

            given(accountRepository.findById(accountId)).willReturn(Optional.of(account));

            assertThatThrownBy(() -> accountService.closeAccount(accountId, userId))
                .isInstanceOf(UnauthorizedAccessException.class);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Account buildAccount(UUID ownerId, AccountStatus status, BigDecimal balance, boolean isPrimary) {
        User accountOwner = new User();
        accountOwner.setId(ownerId);
        return Account.builder()
            .accountNumber("8600" + System.nanoTime() % 1_000_000_000_000L)
            .owner(accountOwner)
            .accountType(AccountType.SAVINGS)
            .currency(Currency.UZS)
            .status(status)
            .balance(balance)
            .isPrimary(isPrimary)
            .dailyTransferLimit(banking.getDailyTransferLimit())
            .monthlyTransferLimit(banking.getMonthlyTransferLimit())
            .build();
    }

    private AccountResponse buildResponse(Account account) {
        return AccountResponse.builder()
            .id(account.getId())
            .accountNumber(account.getAccountNumber())
            .ownerId(account.getOwner().getId())
            .accountType(account.getAccountType())
            .status(account.getStatus())
            .balance(account.getBalance())
            .currency(account.getCurrency())
            .isPrimary(account.isPrimary())
            .build();
    }
}
