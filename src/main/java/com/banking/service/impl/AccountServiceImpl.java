package com.banking.service.impl;

import com.banking.audit.AuditLogService;
import com.banking.config.ApplicationProperties;
import com.banking.dto.request.CreateAccountRequest;
import com.banking.dto.response.AccountResponse;
import com.banking.entity.Account;
import com.banking.entity.User;
import com.banking.enums.AccountStatus;
import com.banking.enums.AuditAction;
import com.banking.exception.BankingException;
import com.banking.exception.ResourceNotFoundException;
import com.banking.exception.UnauthorizedAccessException;
import com.banking.mapper.AccountMapper;
import com.banking.repository.AccountRepository;
import com.banking.repository.TransactionRepository;
import com.banking.repository.UserRepository;
import com.banking.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

/**
 * Implements account lifecycle operations: creation, retrieval, and soft-close.
 *
 * <p>Ownership validation is always performed in this service layer — never
 * delegated to the controller — so that programmatic callers (admin tools, tests)
 * also get the same enforcement.
 *
 * @author Mini Banking API
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private static final String ACCOUNT_NUMBER_PREFIX = "8600";
    private static final int ACCOUNT_NUMBER_SUFFIX_LENGTH = 12;
    private static final int MAX_GENERATION_RETRIES = 10;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final AccountMapper accountMapper;
    private final ApplicationProperties properties;
    private final AuditLogService auditLogService;

    /**
     * {@inheritDoc}
     *
     * @throws BankingException if the user has reached the maximum account limit
     */
    @Override
    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request, UUID userId, String ipAddress, String userAgent) {
        log.debug("Creating account for userId={}, type={}", userId, request.accountType());

        User owner = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        long activeAccountCount = accountRepository.countByOwnerIdAndStatus(userId, AccountStatus.ACTIVE);
        if (activeAccountCount >= properties.getBanking().getMaxAccountsPerUser()) {
            throw new BankingException(
                String.format("Maximum of %d accounts per user reached",
                    properties.getBanking().getMaxAccountsPerUser()),
                HttpStatus.UNPROCESSABLE_ENTITY
            );
        }

        boolean hasPrimary = accountRepository.existsPrimaryAccountForUser(userId);

        // If user requests primary but already has one, demote the existing one
        if (request.isPrimary() && hasPrimary) {
            accountRepository.findPrimaryAccountByOwnerId(userId)
                .ifPresent(existing -> {
                    existing.setPrimary(false);
                    accountRepository.save(existing);
                });
        }

        boolean shouldBePrimary = request.isPrimary() || !hasPrimary;

        Account account = Account.builder()
            .accountNumber(generateUniqueAccountNumber())
            .owner(owner)
            .accountType(request.accountType())
            .currency(request.currency())
            .balance(BigDecimal.ZERO)
            .dailyTransferLimit(properties.getBanking().getDailyTransferLimit())
            .monthlyTransferLimit(properties.getBanking().getMonthlyTransferLimit())
            .isPrimary(shouldBePrimary)
            .status(AccountStatus.ACTIVE)
            .build();

        Account saved = requireNonNull(accountRepository.save(account));

        auditLogService.logSuccess(
            userId.toString(), AuditAction.ACCOUNT_CREATED.name(), "Account",
            saved.getId().toString(), ipAddress, userAgent,
            String.format("{\"accountType\":\"%s\",\"currency\":\"%s\"}",
                request.accountType(), request.currency())
        );

        log.info("Account created: accountId={}, userId={}", saved.getId(), userId);

        return accountMapper.toResponse(saved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> getAccountsByUserId(UUID userId) {
        return accountRepository.findAllByOwnerId(userId).stream()
            .map(accountMapper::toResponse)
            .toList();
    }

    /**
     * {@inheritDoc}
     *
     * @throws ResourceNotFoundException   if the account does not exist
     * @throws UnauthorizedAccessException if the account belongs to another user
     */
    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccountById(UUID accountId, UUID userId) {
        Account account = findAndVerifyOwnership(accountId, userId);
        return accountMapper.toResponse(account);
    }

    /**
     * {@inheritDoc}
     *
     * @throws BankingException if the account has a non-zero balance
     */
    @Override
    @Transactional
    public void closeAccount(UUID accountId, UUID userId, String ipAddress, String userAgent) {
        log.debug("Closing account: accountId={}, userId={}", accountId, userId);

        Account account = findAndVerifyOwnership(accountId, userId);

        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new BankingException("Account is already closed", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        // Prevent closing accounts with remaining balance — funds must be withdrawn first
        if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw new BankingException(
                "Cannot close account with remaining balance. Please withdraw or transfer all funds first.",
                HttpStatus.UNPROCESSABLE_ENTITY
            );
        }

        // Block closure while in-flight transactions are still unsettled
        if (transactionRepository.countPendingByAccountId(accountId) > 0) {
            throw new BankingException(
                "Cannot close account with pending transactions. Please wait for all transactions to settle.",
                HttpStatus.UNPROCESSABLE_ENTITY
            );
        }

        account.setStatus(AccountStatus.CLOSED);
        accountRepository.save(account);

        auditLogService.logSuccess(
            userId.toString(), AuditAction.ACCOUNT_CLOSED.name(), "Account",
            accountId.toString(), ipAddress, userAgent, null
        );

        log.info("Account closed: accountId={}, userId={}", accountId, userId);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Finds an account by ID and verifies it belongs to the requesting user.
     *
     * @throws ResourceNotFoundException   if the account does not exist
     * @throws UnauthorizedAccessException if ownership check fails
     */
    private Account findAndVerifyOwnership(UUID accountId, UUID userId) {
        requireNonNull(accountId, "accountId must not be null");
        requireNonNull(userId, "userId must not be null");

        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));

        if (!Objects.equals(account.getOwner().getId(), userId)) {
            // Log suspicious access attempt with WARN — could indicate IDOR attack
            log.warn("Unauthorized account access attempt: accountId={}, requestingUserId={}", accountId, userId);
            throw new UnauthorizedAccessException("You do not have access to this account");
        }

        return account;
    }

    /**
     * Generates a unique 16-digit account number with the 8600 prefix.
     * Retries on collision (statistically negligible but handled for correctness).
     *
     * @return a unique account number string
     * @throws BankingException if a unique number cannot be generated after max retries
     */
    private String generateUniqueAccountNumber() {
        for (int attempt = 0; attempt < MAX_GENERATION_RETRIES; attempt++) {
            StringBuilder sb = new StringBuilder(ACCOUNT_NUMBER_PREFIX);
            for (int i = 0; i < ACCOUNT_NUMBER_SUFFIX_LENGTH; i++) {
                sb.append(SECURE_RANDOM.nextInt(10));
            }
            String candidate = sb.toString();
            if (!accountRepository.existsByAccountNumber(candidate)) {
                return candidate;
            }
            log.debug("Account number collision on attempt {}, retrying", attempt + 1);
        }
        throw new BankingException("Failed to generate a unique account number. Please retry.",
            HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
