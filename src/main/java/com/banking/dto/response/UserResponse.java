package com.banking.dto.response;

import com.banking.enums.Role;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Public projection of a {@link com.banking.entity.User} entity.
 *
 * <p>Intentionally omits: {@code password}, {@code failedLoginAttempts},
 * {@code lockedUntil}, and internal audit fields that are not relevant to the caller.
 *
 * @author Mini Banking API
 * @version 1.0
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserResponse(

    UUID id,
    String firstName,
    String lastName,
    String email,
    String phoneNumber,
    LocalDate dateOfBirth,
    Role role,
    boolean enabled,
    LocalDateTime createdAt

) {}
