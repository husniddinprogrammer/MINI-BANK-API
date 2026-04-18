package com.banking.mapper;

import com.banking.dto.response.AccountResponse;
import com.banking.entity.Account;
import com.banking.entity.User;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-04-18T22:23:18+0500",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 17.0.12 (Oracle Corporation)"
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
        accountResponse.id( account.getId() );
        accountResponse.accountNumber( account.getAccountNumber() );
        accountResponse.accountType( account.getAccountType() );
        accountResponse.status( account.getStatus() );
        accountResponse.balance( account.getBalance() );
        accountResponse.currency( account.getCurrency() );
        accountResponse.dailyTransferLimit( account.getDailyTransferLimit() );
        accountResponse.monthlyTransferLimit( account.getMonthlyTransferLimit() );
        accountResponse.createdAt( account.getCreatedAt() );

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
