package com.banking.service.impl;

import com.banking.dto.request.UpdateProfileRequest;
import com.banking.dto.response.UserResponse;
import com.banking.entity.User;
import com.banking.exception.DuplicateResourceException;
import com.banking.exception.ResourceNotFoundException;
import com.banking.mapper.UserMapper;
import com.banking.repository.UserRepository;
import com.banking.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Implements user profile retrieval and update operations.
 *
 * <p>Null fields in {@link UpdateProfileRequest} are intentionally skipped
 * (partial update / PATCH semantics) to allow callers to update individual
 * fields without re-submitting unchanged ones.
 *
 * @author Mini Banking API
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    /**
     * {@inheritDoc}
     *
     * @throws ResourceNotFoundException if no user exists with the given ID
     */
    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        return userMapper.toResponse(user);
    }

    /**
     * {@inheritDoc}
     *
     * <p>PATCH semantics: only non-null fields in the request are applied.
     *
     * @throws ResourceNotFoundException   if no user exists with the given ID
     * @throws DuplicateResourceException  if the new phone number is already taken
     */
    @Override
    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        log.debug("Updating profile for userId={}", userId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (StringUtils.hasText(request.firstName())) {
            user.setFirstName(request.firstName().trim());
        }
        if (StringUtils.hasText(request.lastName())) {
            user.setLastName(request.lastName().trim());
        }
        if (StringUtils.hasText(request.phoneNumber())) {
            String newPhone = request.phoneNumber().trim();
            if (!newPhone.equals(user.getPhoneNumber()) && userRepository.existsByPhoneNumber(newPhone)) {
                throw new DuplicateResourceException("User", "phoneNumber", newPhone);
            }
            user.setPhoneNumber(newPhone);
        }
        if (request.dateOfBirth() != null) {
            user.setDateOfBirth(request.dateOfBirth());
        }

        User updated = userRepository.save(user);
        log.info("Profile updated for userId={}", userId);

        return userMapper.toResponse(updated);
    }
}
