package com.banking.mapper;

import com.banking.dto.response.TransactionResponse;
import com.banking.entity.Transaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for converting {@link Transaction} entities to response DTOs.
 *
 * @author Mini Banking API
 * @version 1.0
 */
@Mapper(componentModel = "spring")
public interface TransactionMapper {

    /**
     * Maps a {@link Transaction} entity to its public response DTO.
     * Source and target account UUIDs are extracted; full account objects are not embedded.
     *
     * @param transaction the transaction entity
     * @return the transaction response DTO
     */
    @Mapping(target = "sourceAccountId", source = "sourceAccount.id")
    @Mapping(target = "targetAccountId", source = "targetAccount.id")
    @Mapping(target = "createdAt", source = "createdAt")
    TransactionResponse toResponse(Transaction transaction);
}
