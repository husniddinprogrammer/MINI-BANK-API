package com.banking.controller;

import com.banking.dto.request.CreateAccountRequest;
import com.banking.dto.response.AccountResponse;
import com.banking.dto.response.ApiResponse;
import com.banking.security.SecurityUtils;
import com.banking.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Manages the lifecycle of bank accounts for the authenticated user.
 *
 * <p>Ownership is enforced in the service layer — the controller only
 * extracts the authenticated user's ID and delegates to the service.
 *
 * @author Mini Banking API
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Accounts", description = "Create, view, and close bank accounts")
public class AccountController {

    private final AccountService accountService;

    /**
     * Creates a new bank account for the authenticated user.
     *
     * @param request the account creation payload
     * @return 201 Created with the new account
     */
    @PostMapping
    @Operation(summary = "Create a new account")
    public ResponseEntity<ApiResponse<AccountResponse>> createAccount(
        @Valid @RequestBody CreateAccountRequest request
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        AccountResponse account = accountService.createAccount(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Account created successfully", account));
    }

    /**
     * Returns all accounts owned by the authenticated user.
     *
     * @return 200 OK with the list of accounts
     */
    @GetMapping
    @Operation(summary = "List all accounts for the authenticated user")
    public ResponseEntity<ApiResponse<List<AccountResponse>>> getMyAccounts() {
        UUID userId = SecurityUtils.getCurrentUserId();
        List<AccountResponse> accounts = accountService.getAccountsByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success("Accounts retrieved successfully", accounts));
    }

    /**
     * Returns a single account by ID (ownership verified in service).
     *
     * @param id the account UUID
     * @return 200 OK with the account details
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get account by ID")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccount(@PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId();
        AccountResponse account = accountService.getAccountById(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Account retrieved successfully", account));
    }

    /**
     * Soft-closes an account (sets status to CLOSED).
     * Account must have zero balance to be closed.
     *
     * @param id the account UUID
     * @return 200 OK with confirmation
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Close an account (soft delete)")
    public ResponseEntity<ApiResponse<Void>> closeAccount(@PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId();
        accountService.closeAccount(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Account closed successfully"));
    }
}
