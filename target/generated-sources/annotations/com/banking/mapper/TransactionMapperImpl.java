package com.banking.mapper;

import com.banking.dto.response.TransactionResponse;
import com.banking.entity.Account;
import com.banking.entity.Transaction;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-04-18T22:23:18+0500",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 17.0.12 (Oracle Corporation)"
)
@Component
public class TransactionMapperImpl implements TransactionMapper {

    @Override
    public TransactionResponse toResponse(Transaction transaction) {
        if ( transaction == null ) {
            return null;
        }

        TransactionResponse.TransactionResponseBuilder transactionResponse = TransactionResponse.builder();

        transactionResponse.sourceAccountId( transactionSourceAccountId( transaction ) );
        transactionResponse.targetAccountId( transactionTargetAccountId( transaction ) );
        transactionResponse.createdAt( transaction.getCreatedAt() );
        transactionResponse.id( transaction.getId() );
        transactionResponse.referenceNumber( transaction.getReferenceNumber() );
        transactionResponse.type( transaction.getType() );
        transactionResponse.status( transaction.getStatus() );
        transactionResponse.amount( transaction.getAmount() );
        transactionResponse.fee( transaction.getFee() );
        transactionResponse.balanceBeforeSource( transaction.getBalanceBeforeSource() );
        transactionResponse.balanceAfterSource( transaction.getBalanceAfterSource() );
        transactionResponse.balanceBeforeTarget( transaction.getBalanceBeforeTarget() );
        transactionResponse.balanceAfterTarget( transaction.getBalanceAfterTarget() );
        transactionResponse.currency( transaction.getCurrency() );
        transactionResponse.description( transaction.getDescription() );
        transactionResponse.failureReason( transaction.getFailureReason() );
        transactionResponse.processedAt( transaction.getProcessedAt() );

        return transactionResponse.build();
    }

    private UUID transactionSourceAccountId(Transaction transaction) {
        if ( transaction == null ) {
            return null;
        }
        Account sourceAccount = transaction.getSourceAccount();
        if ( sourceAccount == null ) {
            return null;
        }
        UUID id = sourceAccount.getId();
        if ( id == null ) {
            return null;
        }
        return id;
    }

    private UUID transactionTargetAccountId(Transaction transaction) {
        if ( transaction == null ) {
            return null;
        }
        Account targetAccount = transaction.getTargetAccount();
        if ( targetAccount == null ) {
            return null;
        }
        UUID id = targetAccount.getId();
        if ( id == null ) {
            return null;
        }
        return id;
    }
}
