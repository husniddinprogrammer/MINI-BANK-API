package com.banking.dto.request;

import jakarta.validation.constraints.*;
import lombok.Builder;

import java.time.LocalDate;

/**
 * Payload for user self-registration.
 *
 * <p>Validation is intentionally strict: weak passwords and missing contact
 * details are rejected at the controller boundary before any service logic runs.
 *
 * @param firstName   user's given name (1–100 chars)
 * @param lastName    user's family name (1–100 chars)
 * @param email       valid RFC-5321 email; stored lowercase
 * @param password    must satisfy the bank's complexity policy
 * @param phoneNumber E.164 format enforced by regex
 * @param dateOfBirth optional — used for KYC age checks in future
 *
 * @author Mini Banking API
 * @version 1.0
 */
public record RegisterRequest(

    @NotBlank(message = "First name is required")
    @Size(min = 1, max = 100, message = "First name must be between 1 and 100 characters")
    String firstName,

    @NotBlank(message = "Last name is required")
    @Size(min = 1, max = 100, message = "Last name must be between 1 and 100 characters")
    String lastName,

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    String email,

    /**
     * Password complexity: min 8 chars, at least one uppercase, one lowercase,
     * one digit, one special character.
     * Pattern comment intentionally verbose — security reviewers need to audit this.
     */
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&_\\-#])[A-Za-z\\d@$!%*?&_\\-#]{8,}$",
        message = "Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character"
    )
    String password,

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+998[0-9]{9}$", message = "Phone number must be in E.164 format: +998XXXXXXXXX")
    String phoneNumber,

    @Past(message = "Date of birth must be in the past")
    LocalDate dateOfBirth

) {}
