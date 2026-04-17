package com.banking.service;

import com.banking.dto.request.UpdateProfileRequest;
import com.banking.dto.response.UserResponse;

import java.util.UUID;

/**
 * Contract for user profile management.
 *
 * @author Mini Banking API
 * @version 1.0
 */
public interface UserService {

    /**
     * Returns the public profile of the specified user.
     *
     * @param userId the user's UUID
     * @return the user's public profile
     */
    UserResponse getUserById(UUID userId);

    /**
     * Updates mutable profile fields (name, phone, DOB) for the authenticated user.
     * Email and password changes require separate dedicated flows (not in V1).
     *
     * @param userId  the authenticated user's UUID
     * @param request the fields to update (null fields are skipped)
     * @return the updated user profile
     */
    UserResponse updateProfile(UUID userId, UpdateProfileRequest request);
}
