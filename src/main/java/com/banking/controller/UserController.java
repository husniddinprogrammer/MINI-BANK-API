package com.banking.controller;

import com.banking.dto.request.UpdateProfileRequest;
import com.banking.dto.response.ApiResponse;
import com.banking.dto.response.UserResponse;
import com.banking.security.SecurityUtils;
import com.banking.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Manages the authenticated user's own profile.
 *
 * @author Mini Banking API
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Users", description = "View and update user profile")
public class UserController {

    private final UserService userService;

    /**
     * Returns the authenticated user's own profile.
     *
     * @return 200 OK with the user's public profile
     */
    @GetMapping("/me")
    @Operation(summary = "Get the authenticated user's profile")
    public ResponseEntity<ApiResponse<UserResponse>> getMyProfile() {
        UUID userId = SecurityUtils.getCurrentUserId();
        UserResponse user = userService.getUserById(userId);
        return ResponseEntity.ok(ApiResponse.success("Profile retrieved successfully", user));
    }

    /**
     * Updates mutable profile fields (name, phone, date of birth).
     * Email and password require separate dedicated flows.
     *
     * @param request the fields to update (null fields are skipped)
     * @return 200 OK with the updated profile
     */
    @PutMapping("/me")
    @Operation(summary = "Update the authenticated user's profile")
    public ResponseEntity<ApiResponse<UserResponse>> updateMyProfile(
        @Valid @RequestBody UpdateProfileRequest request
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        UserResponse updated = userService.updateProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", updated));
    }
}
