package com.banking.controller;

import com.banking.dto.request.DepositRequest;
import com.banking.dto.request.TransferRequest;
import com.banking.dto.request.WithdrawRequest;
import com.banking.dto.response.ApiResponse;
import com.banking.dto.response.TransactionResponse;
import com.banking.exception.InvalidIdempotencyKeyException;
import com.banking.security.SecurityUtils;
import com.banking.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.regex.Pattern;

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

    private static final Pattern UUID_PATTERN =
        Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

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
        // HTTP 201 requires Location header pointing to new resource.
        // Since GET /transactions/{id} endpoint does not exist, 200 OK is semantically correct.
        return ResponseEntity.ok(ApiResponse.success("Deposit completed successfully", tx));
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
        // HTTP 201 requires Location header pointing to new resource.
        // Since GET /transactions/{id} endpoint does not exist, 200 OK is semantically correct.
        return ResponseEntity.ok(ApiResponse.success("Withdrawal completed successfully", tx));
    }

    /**
     * Transfers funds between two internal accounts.
     *
     * <p>{@code X-Idempotency-Key} is required and must be a UUID. Submitting the
     * same key twice returns the original result without re-processing — safe for
     * retries under network failures.
     *
     * @param request        the transfer payload
     * @param idempotencyKey UUID idempotency key from header
     * @param httpRequest    for extracting client context
     * @return 201 Created with the transaction record
     */
    @PostMapping("/transfer")
    @Operation(summary = "Transfer funds between accounts")
    public ResponseEntity<ApiResponse<TransactionResponse>> transfer(
        @Valid @RequestBody TransferRequest request,
        @Parameter(
            name = "X-Idempotency-Key",
            description = "UUID idempotency key — resubmitting the same key within the retention window returns the original result without re-processing. Required for safe retries under network failures.",
            required = true,
            schema = @Schema(
                type = "string",
                format = "uuid",
                pattern = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                example = "550e8400-e29b-41d4-a716-446655440000"
            )
        )
        @RequestHeader(value = "X-Idempotency-Key", required = true) String idempotencyKey,
        HttpServletRequest httpRequest
    ) {
        if (!UUID_PATTERN.matcher(idempotencyKey).matches()) {
            throw new InvalidIdempotencyKeyException(idempotencyKey);
        }

        UUID userId = SecurityUtils.getCurrentUserId();
        TransactionResponse tx = transactionService.transfer(
            request, userId, idempotencyKey,
            extractIp(httpRequest), httpRequest.getHeader("User-Agent"));
        // HTTP 201 requires Location header pointing to new resource.
        // Since GET /transactions/{id} endpoint does not exist, 200 OK is semantically correct.
        return ResponseEntity.ok(ApiResponse.success("Transfer completed successfully", tx));
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
