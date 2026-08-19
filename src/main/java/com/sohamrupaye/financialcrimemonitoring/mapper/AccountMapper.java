package com.sohamrupaye.financialcrimemonitoring.mapper;

import com.sohamrupaye.financialcrimemonitoring.dto.AccountResponse;
import com.sohamrupaye.financialcrimemonitoring.model.Account;

/**
 * Turns an {@link Account} entity into the DTO the API returns.
 *
 * <p>Same shape as {@code CustomerMapper}: final class, private constructor,
 * one static method. No state, no dependencies, so there is nothing to inject.
 */
public final class AccountMapper {

    private AccountMapper() {
        // Utility class — never instantiated.
    }

    /**
     * <strong>Must be called from inside the service's transaction.</strong>
     * {@code getCustomer()} is {@code FetchType.LAZY}, so the marked line below
     * hits the database. Call this from a controller instead — after the
     * transaction has closed — and it throws {@code LazyInitializationException}.
     */
    public static AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getAccountNumber(),
                // The flattening AccountResponse was designed around: reach
                // through the relationship and take only the String. This is the
                // lazy load, and the reason this method is transaction-bound.
                account.getCustomer().getCustomerReference(),
                account.getAccountType(),
                account.getCurrency(),
                account.getBalance(),
                account.getStatus(),
                account.getOpenedAt(),
                account.getCreatedAt() // inherited from BaseEntity
        );
    }
}
