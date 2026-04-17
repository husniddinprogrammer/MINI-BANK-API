package com.banking.mapper;

import com.banking.dto.response.AccountResponse;
import com.banking.entity.Account;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for converting {@link Account} entities to response DTOs.
 *
 * @author Mini Banking API
 * @version 1.0
 */
@Mapper(componentModel = "spring")
public interface AccountMapper {

    /**
     * Maps an {@link Account} entity to its public response DTO.
     * Only the owner's UUID is included — not the full {@link com.banking.entity.User} entity —
     * to prevent circular serialization and over-fetching.
     *
     * @param account the account entity
     * @return the account response DTO
     */
    @Mapping(target = "ownerId", source = "owner.id")
    AccountResponse toResponse(Account account);
}
