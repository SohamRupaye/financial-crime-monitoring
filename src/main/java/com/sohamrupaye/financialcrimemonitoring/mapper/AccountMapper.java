package com.sohamrupaye.financialcrimemonitoring.mapper;

import com.sohamrupaye.financialcrimemonitoring.dto.AccountResponse;
import com.sohamrupaye.financialcrimemonitoring.model.Account;

/** Turns an {@link Account} entity into the DTO the API returns. */
public final class AccountMapper {

    private AccountMapper() {
    }

    /**
     * <strong>Must be called from inside the service's transaction.</strong>
     * {@code getCustomer()} is lazy, so the marked line hits the database. Called
     * from a controller, after the transaction has closed, it throws
     * {@code LazyInitializationException}.
     */
    public static AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getAccountNumber(),
                // The lazy load, and the reason this method is transaction-bound.
                account.getCustomer().getCustomerReference(),
                account.getAccountType(),
                account.getCurrency(),
                account.getBalance(),
                account.getStatus(),
                account.getOpenedAt(),
                account.getCreatedAt()
        );
    }
}
