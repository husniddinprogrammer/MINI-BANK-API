package com.banking.service;

import com.banking.dto.request.DepositRequest;
import com.banking.dto.request.TransferRequest;
import com.banking.dto.request.WithdrawRequest;
import com.banking.dto.response.TransactionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Contract for financial transaction operations.
 *
 * <p>All mutating methods are executed within a single database transaction.
 * The transfer method additionally uses pessimistic locking.
 *
 * @author Mini Banking API
 * @version 1.0
 */
public interface TransactionService {

    /**
     * Deposits funds into the specified account.
     *
     * @param request    the deposit payload
     * @param userId     the requesting user's UUID (ownership check)
     * @param ipAddress  client IP for audit
     * @param userAgent  client UA for audit
     * @return the completed transaction record
     */
    TransactionResponse deposit(DepositRequest request, UUID userId, String ipAddress, String userAgent);

    /**
     * Withdraws funds from the specified account.
     *
     * @param request    the withdrawal payload
     * @param userId     the requesting user's UUID (ownership check)
     * @param ipAddress  client IP for audit
     * @param userAgent  client UA for audit
     * @return the completed transaction record
     */
    TransactionResponse withdraw(WithdrawRequest request, UUID userId, String ipAddress, String userAgent);

    /**
     * Transfers funds between two accounts with full atomic guarantees.
     * Uses pessimistic locking and validates limits.
     *
     * @param request         the transfer payload
     * @param userId          the requesting user's UUID (source account ownership check)
     * @param idempotencyKey  optional idempotency key from X-Idempotency-Key header
     * @param ipAddress       client IP for audit
     * @param userAgent       client UA for audit
     * @return the completed transaction record
     */
    TransactionResponse transfer(
        TransferRequest request,
        UUID userId,
        String idempotencyKey,
        String ipAddress,
        String userAgent
    );

    /**
     * Returns paginated transaction history for an account.
     * Verifies that the account belongs to the requesting user.
     *
     * @param accountId the account UUID
     * @param userId    the requesting user's UUID
     * @param pageable  pagination and sort parameters
     * @return a page of transaction responses
     */
    Page<TransactionResponse> getTransactionHistory(UUID accountId, UUID userId, Pageable pageable);
}
