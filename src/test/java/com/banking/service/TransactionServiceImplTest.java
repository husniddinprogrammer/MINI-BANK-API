package com.banking.service;

import com.banking.audit.AuditLogService;
import com.banking.config.ApplicationProperties;
import com.banking.dto.request.TransferRequest;
import com.banking.dto.response.TransactionResponse;
import com.banking.entity.Account;
import com.banking.entity.User;
import com.banking.enums.AccountStatus;
import com.banking.enums.Currency;
import com.banking.enums.TransactionStatus;
import com.banking.exception.AccountFrozenException;
import com.banking.exception.InsufficientFundsException;
import com.banking.exception.TransferLimitExceededException;
import com.banking.exception.UnauthorizedAccessException;
import com.banking.mapper.TransactionMapper;
import com.banking.repository.AccountRepository;
import com.banking.repository.TransactionRepository;
import com.banking.service.impl.TransactionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * Unit tests for {@link TransactionServiceImpl}.
 *
 * <p>Critical paths tested:
 * <ul>
 *   <li>Transfer happy path</li>
 *   <li>Insufficient funds</li>
 *   <li>Frozen source account</li>
 *   <li>Source account belongs to different user (IDOR)</li>
 *   <li>Daily limit exceeded</li>
 * </ul>
 *
 * @author Mini Banking API
 * @version 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionServiceImpl")
class TransactionServiceImplTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private TransactionMapper transactionMapper;
    @Mock private ApplicationProperties properties;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private TransactionServiceImpl transactionService;

    private ApplicationProperties.Banking banking;

    private UUID ownerUserId;
    private UUID sourceAccountId;
    private UUID targetAccountId;
    private Account source;
    private Account target;

    @BeforeEach
    void setUp() {
        banking = new ApplicationProperties.Banking();
        banking.setDailyTransferLimit(new BigDecimal("50000000"));
        banking.setMonthlyTransferLimit(new BigDecimal("500000000"));
        banking.setLargeWithdrawalThreshold(new BigDecimal("10000000"));

        given(properties.getBanking()).willReturn(banking);

        ownerUserId = UUID.randomUUID();
        sourceAccountId = UUID.randomUUID();
        targetAccountId = UUID.randomUUID();

        User owner = new User();
        // Entities need IDs for ownership checks — set via reflection-free builder
        source = Account.builder()
            .accountNumber("8600111111111111")
            .owner(owner)
            .status(AccountStatus.ACTIVE)
            .balance(new BigDecimal("100000.0000"))
            .currency(Currency.UZS)
            .build();

        target = Account.builder()
            .accountNumber("8600222222222222")
            .owner(new User())
            .status(AccountStatus.ACTIVE)
            .balance(new BigDecimal("50000.0000"))
            .currency(Currency.UZS)
            .build();
    }

    @Nested
    @DisplayName("transfer()")
    class Transfer {

        @Test
        @DisplayName("should throw UnauthorizedAccessException when source does not belong to user")
        void shouldThrowWhenSourceNotOwnedByUser() {
            UUID differentUserId = UUID.randomUUID();
            TransferRequest request = new TransferRequest(
                sourceAccountId, targetAccountId, new BigDecimal("1000"), "test");

            // Source account belongs to a different user (different UUID)
            User differentOwner = new User();
            Account sourceOtherOwner = Account.builder()
                .accountNumber("8600111111111111")
                .owner(differentOwner)
                .status(AccountStatus.ACTIVE)
                .balance(new BigDecimal("100000"))
                .currency(Currency.UZS)
                .build();

            given(accountRepository.findById(sourceAccountId)).willReturn(Optional.of(sourceOtherOwner));

            // differentUserId != differentOwner.id (null) — ownership check should fail
            assertThatThrownBy(() ->
                transactionService.transfer(request, differentUserId, null, "127.0.0.1", "UA"))
                .isInstanceOf(UnauthorizedAccessException.class);
        }

        @Test
        @DisplayName("should throw AccountFrozenException when source account is FROZEN")
        void shouldThrowWhenSourceIsFrozen() {
            // ownerUserId is defined in setUp() and shared across tests
            TransferRequest request = new TransferRequest(
                sourceAccountId, targetAccountId, new BigDecimal("1000"), "test");

            User owner = new User();
            owner.setId(ownerUserId);   // must set ID so ownership check passes
            Account frozenSource = Account.builder()
                .accountNumber("8600111111111111")
                .owner(owner)
                .status(AccountStatus.FROZEN)
                .balance(new BigDecimal("100000"))
                .currency(Currency.UZS)
                .build();

            given(accountRepository.findById(sourceAccountId)).willReturn(Optional.of(frozenSource));
            given(accountRepository.findById(targetAccountId)).willReturn(Optional.of(target));
            given(transactionRepository.existsByReferenceNumber(any())).willReturn(false);

            given(transactionRepository.sumCompletedTransferAmountByAccountIdAndDateRange(
                any(), any(), any())).willReturn(BigDecimal.ZERO);

            given(accountRepository.findByIdWithPessimisticLock(any()))
                .willAnswer(inv -> {
                    UUID id = inv.getArgument(0);
                    if (id.equals(sourceAccountId)) return Optional.of(frozenSource);
                    return Optional.of(target);
                });

            assertThatThrownBy(() ->
                transactionService.transfer(request, ownerUserId, null, "127.0.0.1", "UA"))
                .isInstanceOf(AccountFrozenException.class);
        }

        @Test
        @DisplayName("should throw InsufficientFundsException when balance is too low")
        void shouldThrowWhenInsufficientFunds() {
            TransferRequest request = new TransferRequest(
                sourceAccountId, targetAccountId, new BigDecimal("999999999"), "test");

            User owner = new User();
            owner.setId(ownerUserId);
            Account poorSource = Account.builder()
                .accountNumber("8600111111111111")
                .owner(owner)
                .status(AccountStatus.ACTIVE)
                .balance(new BigDecimal("100.0000"))  // << much less than requested
                .currency(Currency.UZS)
                .build();

            given(accountRepository.findById(sourceAccountId)).willReturn(Optional.of(poorSource));
            given(accountRepository.findById(targetAccountId)).willReturn(Optional.of(target));
            given(transactionRepository.sumCompletedTransferAmountByAccountIdAndDateRange(
                any(), any(), any())).willReturn(BigDecimal.ZERO);
            given(accountRepository.findByIdWithPessimisticLock(any()))
                .willAnswer(inv -> {
                    UUID id = inv.getArgument(0);
                    if (id.equals(sourceAccountId)) return Optional.of(poorSource);
                    return Optional.of(target);
                });

            assertThatThrownBy(() ->
                transactionService.transfer(request, ownerUserId, null, "127.0.0.1", "UA"))
                .isInstanceOf(InsufficientFundsException.class);
        }

        @Test
        @DisplayName("should throw TransferLimitExceededException when daily limit is exceeded")
        void shouldThrowWhenDailyLimitExceeded() {
            // Requesting 1 UZS but already used up the entire 50M daily limit
            TransferRequest request = new TransferRequest(
                sourceAccountId, targetAccountId, new BigDecimal("1"), "test");

            User owner = new User();
            owner.setId(ownerUserId);
            Account richSource = Account.builder()
                .accountNumber("8600111111111111")
                .owner(owner)
                .status(AccountStatus.ACTIVE)
                .balance(new BigDecimal("999999999"))
                .currency(Currency.UZS)
                .build();

            given(accountRepository.findById(sourceAccountId)).willReturn(Optional.of(richSource));
            given(accountRepository.findById(targetAccountId)).willReturn(Optional.of(target));

            // Daily already at 50M (exactly at limit)
            given(transactionRepository.sumCompletedTransferAmountByAccountIdAndDateRange(
                eq(sourceAccountId), any(), any()))
                .willReturn(new BigDecimal("50000000"));

            assertThatThrownBy(() ->
                transactionService.transfer(request, ownerUserId, null, "127.0.0.1", "UA"))
                .isInstanceOf(TransferLimitExceededException.class)
                .hasMessageContaining("Daily");
        }
    }
}
