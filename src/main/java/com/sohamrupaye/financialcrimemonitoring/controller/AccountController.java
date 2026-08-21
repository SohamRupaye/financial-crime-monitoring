package com.sohamrupaye.financialcrimemonitoring.controller;

import com.sohamrupaye.financialcrimemonitoring.dto.AccountResponse;
import com.sohamrupaye.financialcrimemonitoring.dto.CreateAccountRequest;
import com.sohamrupaye.financialcrimemonitoring.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * HTTP entry point for accounts.
 *
 * <p>Mapped at {@code /api/v1} rather than one resource path because accounts are
 * addressed two ways: nested under their owning customer for creation and listing,
 * and directly by account number for lookup. Splitting those across two
 * controllers would put one resource in two files.
 */
@RestController
@RequestMapping("/api/v1")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /** {@code POST /api/v1/customers/CUST-3F2A9C41/accounts} */
    @PostMapping("/customers/{customerReference}/accounts")
    public ResponseEntity<AccountResponse> open(
            @PathVariable String customerReference,
            @Valid @RequestBody CreateAccountRequest request,
            UriComponentsBuilder uriBuilder) {

        AccountResponse created = accountService.create(customerReference, request);

        // Points at the canonical single-account URL, not the nested one it was
        // created through.
        URI location = uriBuilder
                .path("/api/v1/accounts/{accountNumber}")
                .buildAndExpand(created.accountNumber())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }

    /**
     * {@code GET /api/v1/customers/CUST-3F2A9C41/accounts}
     *
     * <p>Not paginated: an account list per customer is bounded by how many
     * accounts a person opens. Transactions are the collection that needs paging.
     */
    @GetMapping("/customers/{customerReference}/accounts")
    public List<AccountResponse> listForCustomer(@PathVariable String customerReference) {
        return accountService.findByCustomerReference(customerReference);
    }

    /** {@code GET /api/v1/accounts/ACC-9B41C7E20D5A} */
    @GetMapping("/accounts/{accountNumber}")
    public AccountResponse get(@PathVariable String accountNumber) {
        return accountService.findByAccountNumber(accountNumber);
    }
}
