package com.banking.mapper;

import com.banking.dto.response.AccountResponse;
import com.banking.entity.Account;
import com.banking.entity.User;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-04-21T06:31:14+0500",
    comments = "version: 1.5.5.Final, compiler: Eclipse JDT (IDE) 3.46.0.v20260407-0427, environment: Java 21.0.10 (Eclipse Adoptium)"
)
@Component
public class AccountMapperImpl implements AccountMapper {

    @Override
    public AccountResponse toResponse(Account account) {
        if ( account == null ) {
            return null;
        }

        AccountResponse.AccountResponseBuilder accountResponse = AccountResponse.builder();

        accountResponse.ownerId( accountOwnerId( account ) );
        accountResponse.accountNumber( account.getAccountNumber() );
        accountResponse.accountType( account.getAccountType() );
        accountResponse.balance( account.getBalance() );
        accountResponse.createdAt( account.getCreatedAt() );
        accountResponse.currency( account.getCurrency() );
        accountResponse.dailyTransferLimit( account.getDailyTransferLimit() );
        accountResponse.id( account.getId() );
        accountResponse.monthlyTransferLimit( account.getMonthlyTransferLimit() );
        accountResponse.status( account.getStatus() );

        return accountResponse.build();
    }

    private UUID accountOwnerId(Account account) {
        if ( account == null ) {
            return null;
        }
        User owner = account.getOwner();
        if ( owner == null ) {
            return null;
        }
        UUID id = owner.getId();
        if ( id == null ) {
            return null;
        }
        return id;
    }
}
