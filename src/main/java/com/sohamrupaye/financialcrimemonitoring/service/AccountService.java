package com.sohamrupaye.financialcrimemonitoring.service;

import com.sohamrupaye.financialcrimemonitoring.dto.AccountResponse;
import com.sohamrupaye.financialcrimemonitoring.dto.CreateAccountRequest;
import com.sohamrupaye.financialcrimemonitoring.exception.ResourceNotFoundException;
import com.sohamrupaye.financialcrimemonitoring.mapper.AccountMapper;
import com.sohamrupaye.financialcrimemonitoring.model.Account;
import com.sohamrupaye.financialcrimemonitoring.model.Customer;
import com.sohamrupaye.financialcrimemonitoring.model.enums.AccountStatus;
import com.sohamrupaye.financialcrimemonitoring.repository.AccountRepository;
import com.sohamrupaye.financialcrimemonitoring.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Business rules for accounts.
 *
 * <p>Structurally identical to {@code CustomerService} — the only new thing is
 * that it has two dependencies instead of one.
 */
@Service
@Transactional(readOnly = true)
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private static final String ACCOUNT_NUMBER_PREFIX = "ACC-";

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;

    public AccountService(AccountRepository accountRepository,
                          CustomerRepository customerRepository) {
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
    }

    /**
     * One account by its number. 404 when there is no such account.
     */
    public AccountResponse findByAccountNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .map(AccountMapper::toResponse)
                .orElseThrow(() -> ResourceNotFoundException.of("Account", accountNumber));
    }

    /**
     * Every account belonging to one customer.
     *
     * <p>Note the customer lookup on the first line even though the query below
     * does not need it. Without it, an unknown reference would return an empty
     * list — indistinguishable from "this customer exists and has no accounts" —
     * and the endpoint would answer 200 instead of the required 404. The lookup
     * is what buys the 404.
     */
    public List<AccountResponse> findByCustomerReference(String customerReference) {
        requireCustomer(customerReference);

        return accountRepository.findByCustomer_CustomerReference(customerReference)
                .stream()
                .map(AccountMapper::toResponse)
                .toList();
    }

    /**
     * Opens an account for an existing customer.
     *
     * <p>{@code @Transactional} without {@code readOnly} — this writes. The
     * customer lookup and the insert share one transaction.
     */
    @Transactional
    public AccountResponse create(String customerReference, CreateAccountRequest request) {
        // 404 before anything else. Nothing is written if the owner is unknown.
        Customer customer = requireCustomer(customerReference);

        Account account = new Account(
                // Server-generated, exactly like CustomerService builds CUST-.
                generateAccountNumber(),
                customer,
                request.currency().toUpperCase(Locale.ROOT),
                // Every account opens empty. Money arrives through transactions,
                // never at creation — which is precisely why CreateAccountRequest
                // has no balance field to read this from.
                BigDecimal.ZERO,
                LocalDate.now(),
                request.accountType(),
                // Server-assigned too, so nobody can open a pre-FROZEN account.
                AccountStatus.ACTIVE
        );

        Account saved = accountRepository.save(account);
        log.info("Opened account {} for customer {}",
                saved.getAccountNumber(), customerReference);

        // Mapping happens here, inside the transaction, so the mapper's lazy
        // getCustomer() call still has an open session to work with.
        return AccountMapper.toResponse(saved);
    }

    /**
     * Loads a customer or throws. Both public methods need this, and neither
     * should be reachable with a bad reference, so it lives in one place.
     */
    private Customer requireCustomer(String customerReference) {
        return customerRepository.findByCustomerReference(customerReference)
                .orElseThrow(() -> ResourceNotFoundException.of("Customer", customerReference));
    }

    /**
     * {@code ACC-} plus 12 hex characters.
     *
     * <p>The {@code replace} matters: a UUID string has hyphens at fixed
     * positions, so taking the first 12 characters without stripping them would
     * embed one in the account number.
     */
    private String generateAccountNumber() {
        return ACCOUNT_NUMBER_PREFIX + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase(Locale.ROOT);
    }
}
