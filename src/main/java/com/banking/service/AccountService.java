package com.banking.service;

import com.banking.dto.request.CreateAccountRequest;
import com.banking.dto.response.AccountResponse;

import java.util.List;
import java.util.UUID;

/**
 * Contract for account lifecycle management.
 *
 * <p>All methods enforce ownership: a user can only access/modify their own accounts
 * unless they hold the ADMIN role.
 *
 * @author Mini Banking API
 * @version 1.0
 */
public interface AccountService {

    /**
     * Creates a new bank account for the authenticated user.
     *
     * @param request    the account creation payload
     * @param userId     the owner's UUID
     * @param ipAddress  caller's IP for audit logging
     * @param userAgent  caller's User-Agent for audit logging
     * @return the created account
     */
    AccountResponse createAccount(CreateAccountRequest request, UUID userId, String ipAddress, String userAgent);

    /**
     * Returns all accounts owned by the given user.
     *
     * @param userId the owner's UUID
     * @return list of accounts (may be empty)
     */
    List<AccountResponse> getAccountsByUserId(UUID userId);

    /**
     * Returns a single account by ID, verifying ownership.
     *
     * @param accountId the account UUID
     * @param userId    the requesting user's UUID
     * @return the account response
     */
    AccountResponse getAccountById(UUID accountId, UUID userId);

    /**
     * Soft-closes an account (sets status to CLOSED).
     * Fails if the account has a non-zero balance.
     *
     * @param accountId  the account UUID
     * @param userId     the requesting user's UUID (ownership check)
     * @param ipAddress  caller's IP for audit logging
     * @param userAgent  caller's User-Agent for audit logging
     */
    void closeAccount(UUID accountId, UUID userId, String ipAddress, String userAgent);
}
