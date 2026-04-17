package com.banking.controller;

import com.banking.dto.request.DepositRequest;
import com.banking.dto.request.TransferRequest;
import com.banking.dto.request.WithdrawRequest;
import com.banking.dto.response.ApiResponse;
import com.banking.dto.response.TransactionResponse;
import com.banking.security.SecurityUtils;
import com.banking.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Handles financial transactions: deposits, withdrawals, transfers, and history.
 *
 * <p>The transfer endpoint accepts an optional {@code X-Idempotency-Key} header.
 * If the same key is submitted twice, the original result is returned without
 * re-processing — safe for retries under network failures.
 *
 * @author Mini Banking API
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Transactions", description = "Deposit, withdraw, transfer, and view history")
public class TransactionController {

    private final TransactionService transactionService;

    /**
     * Deposits funds into the specified account.
     *
     * @param request     the deposit payload
     * @param httpRequest for extracting client context for audit
     * @return 201 Created with the transaction record
     */
    @PostMapping("/deposit")
    @Operation(summary = "Deposit funds into an account")
    public ResponseEntity<ApiResponse<TransactionResponse>> deposit(
        @Valid @RequestBody DepositRequest request,
        HttpServletRequest httpRequest
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        TransactionResponse tx = transactionService.deposit(
            request, userId, extractIp(httpRequest), httpRequest.getHeader("User-Agent"));
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Deposit completed successfully", tx));
    }

    /**
     * Withdraws funds from the specified account.
     *
     * @param request     the withdrawal payload
     * @param httpRequest for extracting client context
     * @return 201 Created with the transaction record
     */
    @PostMapping("/withdraw")
    @Operation(summary = "Withdraw funds from an account")
    public ResponseEntity<ApiResponse<TransactionResponse>> withdraw(
        @Valid @RequestBody WithdrawRequest request,
        HttpServletRequest httpRequest
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        TransactionResponse tx = transactionService.withdraw(
            request, userId, extractIp(httpRequest), httpRequest.getHeader("User-Agent"));
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Withdrawal completed successfully", tx));
    }

    /**
     * Transfers funds between two internal accounts.
     *
     * <p>Supply {@code X-Idempotency-Key} to make retries safe.
     *
     * @param request         the transfer payload
     * @param idempotencyKey  optional idempotency key (from header)
     * @param httpRequest     for extracting client context
     * @return 201 Created with the transaction record
     */
    @PostMapping("/transfer")
    @Operation(summary = "Transfer funds between accounts")
    public ResponseEntity<ApiResponse<TransactionResponse>> transfer(
        @Valid @RequestBody TransferRequest request,
        @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest httpRequest
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        TransactionResponse tx = transactionService.transfer(
            request, userId, idempotencyKey,
            extractIp(httpRequest), httpRequest.getHeader("User-Agent"));
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Transfer completed successfully", tx));
    }

    /**
     * Returns paginated transaction history for the specified account.
     * Defaults to 10 per page, sorted by creation time descending.
     *
     * @param accountId the account UUID
     * @param pageable  pagination parameters (page, size, sort)
     * @return 200 OK with a page of transaction records
     */
    @GetMapping("/{accountId}/history")
    @Operation(summary = "Get paginated transaction history for an account")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> getHistory(
        @PathVariable UUID accountId,
        @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        Page<TransactionResponse> history = transactionService.getTransactionHistory(accountId, userId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Transaction history retrieved", history));
    }

    private String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
