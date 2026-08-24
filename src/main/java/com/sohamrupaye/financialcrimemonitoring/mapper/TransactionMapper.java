package com.sohamrupaye.financialcrimemonitoring.mapper;

import com.sohamrupaye.financialcrimemonitoring.dto.TransactionResponse;
import com.sohamrupaye.financialcrimemonitoring.model.Transaction;

public final class TransactionMapper {

    private TransactionMapper() {
    }

    /**
     * Must run inside a transaction: {@code account} and its {@code customer} are
     * both lazy. Repository methods that feed this use an entity graph so the two
     * arrive in the same query instead of one per row.
     */
    public static TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getTransactionReference(),
                transaction.getAccount().getAccountNumber(),
                transaction.getAccount().getCustomer().getCustomerReference(),
                transaction.getTransactionType(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getCounterpartyAccountNumber(),
                transaction.getCounterpartyCountry(),
                transaction.getOccurredAt(),
                // createdAt is when we were told, which is the honest name for it
                // in an API about money that moved earlier.
                transaction.getCreatedAt()
        );
    }
}
