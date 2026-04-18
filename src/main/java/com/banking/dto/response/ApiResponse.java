package com.banking.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Generic API response envelope wrapping all endpoints' responses.
 *
 * <p>Every response — success or error — is wrapped in this structure so
 * clients always receive a consistent schema:
 * <pre>
 * {
 *   "success": true,
 *   "message": "Account created successfully",
 *   "data": { ... },
 *   "timestamp": "2024-01-15T10:30:00"
 * }
 * </pre>
 *
 * @param <T> the type of the payload data
 *
 * @author Mini Banking API
 * @version 1.0
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(

    boolean success,
    String message,
    T data,
    LocalDateTime timestamp

) {

    /**
     * Convenience factory for successful responses with data.
     *
     * @param message human-readable success message
     * @param data    the response payload
     * @param <T>     payload type
     * @return wrapped success response
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
            .success(true)
            .message(message)
            .data(data)
            .timestamp(LocalDateTime.now(ZoneOffset.UTC))
            .build();
    }

    /**
     * Convenience factory for successful responses without data (e.g. logout).
     *
     * @param message human-readable success message
     * @param <T>     phantom type parameter
     * @return wrapped success response
     */
    public static <T> ApiResponse<T> success(String message) {
        return ApiResponse.<T>builder()
            .success(true)
            .message(message)
            .timestamp(LocalDateTime.now(ZoneOffset.UTC))
            .build();
    }

}
