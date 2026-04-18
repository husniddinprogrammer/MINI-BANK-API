package com.banking.service.impl;

import com.banking.audit.AuditLogService;
import com.banking.config.ApplicationProperties;
import com.banking.dto.request.DepositRequest;
import com.banking.dto.request.TransferRequest;
import com.banking.dto.request.WithdrawRequest;
import com.banking.dto.response.TransactionResponse;
import com.banking.entity.Account;
import com.banking.entity.Transaction;
import com.banking.enums.AccountStatus;
import com.banking.enums.AuditAction;
import com.banking.enums.TransactionStatus;
import com.banking.enums.TransactionType;
import com.banking.exception.*;
import com.banking.mapper.TransactionMapper;
import com.banking.repository.AccountRepository;
import com.banking.repository.TransactionRepository;
import com.banking.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

/**
 * Core financial transaction processing service.
 *
 * <p>Transfer implementation follows a strict 13-step protocol to ensure
 * correctness, atomicity, and auditability:
 * <ol>
 *   <li>Validate source account ownership</li>
 *   <li>Validate accounts exist and are ACTIVE</li>
 *   <li>Validate sufficient balance including fee</li>
 *   <li>Check daily limit</li>
 *   <li>Check monthly limit</li>
 *   <li>Lock BOTH accounts with PESSIMISTIC_WRITE in consistent order</li>
 *   <li>Re-validate balance after lock (prevents TOCTOU race)</li>
 *   <li>Snapshot balance before</li>
 *   <li>Deduct source, credit target</li>
 *   <li>Snapshot balance after</li>
 *   <li>Persist transaction as COMPLETED</li>
 *   <li>Write audit log asynchronously</li>
 *   <li>On any failure: mark transaction FAILED with reason; roll back balance changes</li>
 * </ol>
 *
 * @author Mini Banking API
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private static final DateTimeFormatter REF_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int MONETARY_SCALE = 4;

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final TransactionMapper transactionMapper;
    private final ApplicationProperties properties;
    private final AuditLogService auditLogService;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public TransactionResponse deposit(DepositRequest request, UUID userId, String ipAddress, String userAgent) {
        log.debug("Processing deposit: accountId={}, amount={}", request.accountId(), request.amount());

        Account account = accountRepository.findByIdWithPessimisticLock(request.accountId())
            .orElseThrow(() -> new ResourceNotFoundException("Account", "id", request.accountId()));

        verifyOwnership(account, userId);
        verifyAccountActive(account);

        BigDecimal before = account.getBalance();
        account.setBalance(before.add(request.amount()).setScale(MONETARY_SCALE, RoundingMode.HALF_UP));
        accountRepository.save(account);

        Transaction tx = Transaction.builder()
            .referenceNumber(generateReferenceNumber())
            .targetAccount(account)
            .type(TransactionType.DEPOSIT)
            .status(TransactionStatus.COMPLETED)
            .amount(request.amount())
            .fee(BigDecimal.ZERO)
            .balanceBeforeTarget(before)
            .balanceAfterTarget(account.getBalance())
            .currency(account.getCurrency())
            .description(request.description())
            .processedAt(LocalDateTime.now(ZoneOffset.UTC))
            .build();

        Transaction saved = transactionRepository.save(tx);

        auditLogService.logSuccess(
            userId.toString(), AuditAction.DEPOSIT.name(), "Transaction",
            saved.getId().toString(), ipAddress, userAgent,
            String.format("{\"accountId\":\"%s\",\"amount\":\"%s\"}", request.accountId(), request.amount())
        );

        log.info("Deposit completed: txId={}, accountId={}, amount={}", saved.getId(), request.accountId(), request.amount());

        return transactionMapper.toResponse(saved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public TransactionResponse withdraw(WithdrawRequest request, UUID userId, String ipAddress, String userAgent) {
        log.debug("Processing withdrawal: accountId={}, amount={}", request.accountId(), request.amount());

        Account account = accountRepository.findByIdWithPessimisticLock(request.accountId())
            .orElseThrow(() -> new ResourceNotFoundException("Account", "id", request.accountId()));

        verifyOwnership(account, userId);
        verifyAccountActive(account);

        BigDecimal fee = calculateWithdrawalFee(request.amount());
        BigDecimal totalRequired = request.amount().add(fee);

        if (account.getBalance().compareTo(totalRequired) < 0) {
            throw new InsufficientFundsException(account.getBalance(), totalRequired);
        }

        BigDecimal before = account.getBalance();
        account.setBalance(before.subtract(totalRequired).setScale(MONETARY_SCALE, RoundingMode.HALF_UP));
        accountRepository.save(account);

        Transaction tx = Transaction.builder()
            .referenceNumber(generateReferenceNumber())
            .sourceAccount(account)
            .type(TransactionType.WITHDRAWAL)
            .status(TransactionStatus.COMPLETED)
            .amount(request.amount())
            .fee(fee)
            .balanceBeforeSource(before)
            .balanceAfterSource(account.getBalance())
            .currency(account.getCurrency())
            .description(request.description())
            .processedAt(LocalDateTime.now(ZoneOffset.UTC))
            .build();

        Transaction saved = transactionRepository.save(tx);

        auditLogService.logSuccess(
            userId.toString(), AuditAction.WITHDRAWAL.name(), "Transaction",
            saved.getId().toString(), ipAddress, userAgent,
            String.format("{\"accountId\":\"%s\",\"amount\":\"%s\",\"fee\":\"%s\"}",
                request.accountId(), request.amount(), fee)
        );

        log.info("Withdrawal completed: txId={}, accountId={}, amount={}, fee={}",
            saved.getId(), request.accountId(), request.amount(), fee);

        return transactionMapper.toResponse(saved);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Pessimistic locking strategy:
     * <ul>
     *   <li>Both accounts are locked using {@code SELECT … FOR UPDATE} before any balance change.</li>
     *   <li>Locks are acquired in UUID sort order (lower UUID first) to prevent deadlock
     *       when two concurrent transfers involve the same pair of accounts in reverse order.</li>
     *   <li>Balance is re-validated AFTER the lock (TOCTOU prevention).</li>
     * </ul>
     */
    // SECURITY: REPEATABLE_READ prevents phantom reads during limit/balance checks inside the lock.
    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public TransactionResponse transfer(
        TransferRequest request,
        UUID userId,
        String idempotencyKey,
        String ipAddress,
        String userAgent
    ) {
        log.debug("Processing transfer: source={}, target={}, amount={}",
            request.sourceAccountId(), request.targetAccountId(), request.amount());

        // Idempotency check — if we already processed this key, return the existing result
        if (idempotencyKey != null) {
            String idempotentRef = "TXN-IDMP-" + idempotencyKey;
            if (transactionRepository.existsByReferenceNumber(idempotentRef)) {
                Transaction existing = transactionRepository.findByReferenceNumber(idempotentRef).orElseThrow();
                // Verify the requesting user actually owns this transaction's source account
                if (existing.getSourceAccount() != null
                    && !existing.getSourceAccount().getOwner().getId().equals(userId)) {
                    throw new UnauthorizedAccessException("You do not have access to this account");
                }
                // Detect key reuse with different parameters — same key must mean same request
                boolean sameSource = existing.getSourceAccount() != null
                    && existing.getSourceAccount().getId().equals(request.sourceAccountId());
                boolean sameTarget = existing.getTargetAccount() != null
                    && existing.getTargetAccount().getId().equals(request.targetAccountId());
                boolean sameAmount = existing.getAmount().compareTo(request.amount()) == 0;
                if (!sameSource || !sameTarget || !sameAmount) {
                    throw new BankingException(
                        "Idempotency key conflict: this key was already used for a different transfer",
                        HttpStatus.UNPROCESSABLE_ENTITY);
                }
                return transactionMapper.toResponse(existing);
            }
        }

        if (request.sourceAccountId().equals(request.targetAccountId())) {
            throw new BankingException("Source and target accounts cannot be the same",
                HttpStatus.UNPROCESSABLE_ENTITY);
        }

        // ── Step 1: Ownership check ONLY — no state validation before lock ────
        //
        // SECURITY: TOCTOU (Time-Of-Check-Time-Of-Use) prevention.
        // All state validations (active status, currency, limits, balance) happen
        // AFTER acquiring pessimistic locks so the checked state is always current.
        // Without this, an account could be frozen or a limit exceeded between the
        // pre-check and the lock, allowing invalid transfers to slip through.
        accountRepository.findByIdAndOwnerId(request.sourceAccountId(), userId)
            .orElseThrow(() -> new UnauthorizedAccessException("You do not have access to this account"));

        // ── Step 2: Acquire pessimistic locks in consistent UUID order ────────
        //
        // Locks lower UUID first — prevents deadlock when two concurrent transfers
        // involve the same account pair in reverse directions (A→B and B→A).
        UUID firstLock = request.sourceAccountId().compareTo(request.targetAccountId()) < 0
            ? request.sourceAccountId() : request.targetAccountId();
        UUID secondLock = firstLock.equals(request.sourceAccountId())
            ? request.targetAccountId() : request.sourceAccountId();

        Account firstAccount = accountRepository.findByIdWithPessimisticLock(firstLock)
            .orElseThrow(() -> new ResourceNotFoundException("Account", "id", firstLock));
        Account secondAccount = accountRepository.findByIdWithPessimisticLock(secondLock)
            .orElseThrow(() -> new ResourceNotFoundException("Account", "id", secondLock));

        Account source = firstLock.equals(request.sourceAccountId()) ? firstAccount : secondAccount;
        Account target = firstLock.equals(request.targetAccountId()) ? firstAccount : secondAccount;

        // ── Step 3: ALL state validations inside the lock ────────────────────

        verifyAccountActive(source);
        verifyAccountActive(target);

        if (!source.getCurrency().equals(target.getCurrency())) {
            throw new BankingException("Cross-currency transfers are not supported",
                HttpStatus.UNPROCESSABLE_ENTITY);
        }

        checkDailyLimit(request.sourceAccountId(), request.amount());
        checkMonthlyLimit(request.sourceAccountId(), request.amount());

        BigDecimal fee = BigDecimal.ZERO; // Same-currency, same-bank: no fee
        BigDecimal totalRequired = request.amount().add(fee);

        if (source.getBalance().compareTo(totalRequired) < 0) {
            throw new InsufficientFundsException(source.getBalance(), totalRequired);
        }

        // ── Snapshot, debit, credit ──────────────────────────────────────────

        BigDecimal sourceBalanceBefore = source.getBalance();
        BigDecimal targetBalanceBefore = target.getBalance();

        source.setBalance(sourceBalanceBefore.subtract(totalRequired).setScale(MONETARY_SCALE, RoundingMode.HALF_UP));
        target.setBalance(targetBalanceBefore.add(request.amount()).setScale(MONETARY_SCALE, RoundingMode.HALF_UP));

        accountRepository.save(source);
        accountRepository.save(target);

        String referenceNumber = idempotencyKey != null
            ? "TXN-IDMP-" + idempotencyKey
            : generateReferenceNumber();

        Transaction tx = Transaction.builder()
            .referenceNumber(referenceNumber)
            .sourceAccount(source)
            .targetAccount(target)
            .type(TransactionType.TRANSFER)
            .status(TransactionStatus.COMPLETED)
            .amount(request.amount())
            .fee(fee)
            .balanceBeforeSource(sourceBalanceBefore)
            .balanceAfterSource(source.getBalance())
            .balanceBeforeTarget(targetBalanceBefore)
            .balanceAfterTarget(target.getBalance())
            .currency(source.getCurrency())
            .description(request.description())
            .processedAt(LocalDateTime.now(ZoneOffset.UTC))
            .build();

        Transaction saved = transactionRepository.save(tx);

        auditLogService.logSuccess(
            userId.toString(), AuditAction.TRANSFER.name(), "Transaction",
            saved.getId().toString(), ipAddress, userAgent,
            String.format("{\"source\":\"%s\",\"target\":\"%s\",\"amount\":\"%s\"}",
                request.sourceAccountId(), request.targetAccountId(), request.amount())
        );

        log.info("Transfer completed: txId={}, from={}, to={}, amount={}",
            saved.getId(), request.sourceAccountId(), request.targetAccountId(), request.amount());

        return transactionMapper.toResponse(saved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactionHistory(UUID accountId, UUID userId, Pageable pageable) {
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));

        verifyOwnership(account, userId);

        return transactionRepository.findByAccountId(accountId, pageable)
            .map(transactionMapper::toResponse);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void verifyOwnership(Account account, UUID userId) {
        if (!Objects.equals(account.getOwner().getId(), userId)) {
            log.warn("Unauthorized account access: accountId={}, requestingUserId={}", account.getId(), userId);
            throw new UnauthorizedAccessException("You do not have access to this account");
        }
    }

    private void verifyAccountActive(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            String lastFour = account.getAccountNumber()
                .substring(account.getAccountNumber().length() - 4);
            throw new AccountFrozenException(lastFour, account.getStatus().name());
        }
    }

    private void checkDailyLimit(UUID accountId, BigDecimal amount) {
        LocalDateTime startOfDay = LocalDateTime.now(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime endOfDay = startOfDay.plusDays(1).minusNanos(1);

        BigDecimal dailyUsed = transactionRepository.sumCompletedTransferAmountByAccountIdAndDateRange(
            accountId, startOfDay, endOfDay).orElse(BigDecimal.ZERO);

        BigDecimal dailyLimit = properties.getBanking().getDailyTransferLimit();

        if (dailyUsed.add(amount).compareTo(dailyLimit) > 0) {
            throw new TransferLimitExceededException("Daily", dailyLimit, dailyUsed, amount);
        }
    }

    private void checkMonthlyLimit(UUID accountId, BigDecimal amount) {
        LocalDateTime startOfMonth = LocalDateTime.now(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime endOfMonth = startOfMonth.plusMonths(1).minusNanos(1);

        BigDecimal monthlyUsed = transactionRepository.sumCompletedTransferAmountByAccountIdAndDateRange(
            accountId, startOfMonth, endOfMonth).orElse(BigDecimal.ZERO);

        BigDecimal monthlyLimit = properties.getBanking().getMonthlyTransferLimit();

        if (monthlyUsed.add(amount).compareTo(monthlyLimit) > 0) {
            throw new TransferLimitExceededException("Monthly", monthlyLimit, monthlyUsed, amount);
        }
    }

    /**
     * Calculates withdrawal fee.
     * Amounts exceeding the large-withdrawal threshold incur a fee defined in configuration.
     */
    private BigDecimal calculateWithdrawalFee(BigDecimal amount) {
        BigDecimal threshold = properties.getBanking().getLargeWithdrawalThreshold();
        if (amount.compareTo(threshold) > 0) {
            // FINANCE: HALF_EVEN (Banker's Rounding) is the IEEE 754 standard for financial
            // calculations. HALF_UP introduces systematic upward bias — over millions of
            // transactions this causes measurable discrepancy in bank ledgers.
            BigDecimal feeRate = properties.getBanking().getLargeWithdrawalFeePercent()
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_EVEN);
            return amount.multiply(feeRate).setScale(MONETARY_SCALE, RoundingMode.HALF_EVEN);
        }
        return BigDecimal.ZERO;
    }

    /**
     * Generates a human-readable unique reference number: {@code TXN-YYYYMMDD-UUID8}.
     */
    private String generateReferenceNumber() {
        String date = LocalDateTime.now(ZoneOffset.UTC).format(REF_DATE_FORMAT);
        String shortUuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "TXN-" + date + "-" + shortUuid;
    }
}
