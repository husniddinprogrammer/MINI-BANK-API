package com.banking.dto.request;

import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Payload for updating mutable profile fields.
 *
 * <p>Email and password are intentionally excluded — they require separate
 * dedicated flows (email-change verification, password-change with current-password
 * confirmation) not yet implemented in V1.
 *
 * @param firstName   optional new first name
 * @param lastName    optional new last name
 * @param phoneNumber optional new E.164 phone number
 * @param dateOfBirth optional new date of birth
 *
 * @author Mini Banking API
 * @version 1.0
 */
public record UpdateProfileRequest(

    @Size(min = 1, max = 100, message = "First name must be between 1 and 100 characters")
    String firstName,

    @Size(min = 1, max = 100, message = "Last name must be between 1 and 100 characters")
    String lastName,

    @Pattern(regexp = "^\\+998[0-9]{9}$", message = "Phone number must be in E.164 format: +998XXXXXXXXX")
    String phoneNumber,

    @Past(message = "Date of birth must be in the past")
    LocalDate dateOfBirth

) {}
