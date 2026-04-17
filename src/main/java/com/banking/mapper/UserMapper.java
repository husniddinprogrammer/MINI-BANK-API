package com.banking.mapper;

import com.banking.dto.request.RegisterRequest;
import com.banking.dto.response.UserResponse;
import com.banking.entity.User;
import org.mapstruct.*;

/**
 * MapStruct mapper for converting between {@link User} entity and its DTOs.
 *
 * <p>{@code componentModel = "spring"} declared explicitly on the annotation (not just
 * via compiler arg) so MapStruct generates a {@code @Component}-annotated implementation
 * class that Spring can discover regardless of build tool or IDE compilation mode.
 *
 * <p>The {@code password} field is intentionally NEVER mapped to any response DTO.
 *
 * @author Mini Banking API
 * @version 1.0
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    /**
     * Converts a {@link User} entity to a safe public response DTO.
     * Password and internal lockout fields are excluded by the DTO definition.
     *
     * @param user the user entity
     * @return the public user response
     */
    @Mapping(target = "id", source = "id")
    @Mapping(target = "createdAt", source = "createdAt")
    UserResponse toResponse(User user);

    /**
     * Creates a new {@link User} entity from a registration request.
     *
     * <p>Password is intentionally NOT mapped here — it must be BCrypt-hashed
     * by the service layer before being set on the entity.
     *
     * <p>{@code disableBuilder = true} forces MapStruct to use the no-args constructor
     * + setters instead of Lombok's {@code @Builder}. Lombok's builder only covers fields
     * declared in {@code User} itself; fields inherited from {@code BaseEntity} (id,
     * createdAt, etc.) are invisible to {@code User.UserBuilder}, causing a compile error
     * when MapStruct tries to add {@code ignore} mappings for them.
     *
     * @param request the registration request
     * @return a partially populated {@link User} (password must be set separately)
     */
    @BeanMapping(builder = @Builder(disableBuilder = true))
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "enabled", ignore = true)
    @Mapping(target = "accountNonLocked", ignore = true)
    @Mapping(target = "failedLoginAttempts", ignore = true)
    @Mapping(target = "lockedUntil", ignore = true)
    @Mapping(target = "accounts", ignore = true)
    @Mapping(target = "refreshTokens", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    // UserDetails.getAuthorities() is derived from role — not a mappable field
    @Mapping(target = "authorities", ignore = true)
    User toEntity(RegisterRequest request);
}
