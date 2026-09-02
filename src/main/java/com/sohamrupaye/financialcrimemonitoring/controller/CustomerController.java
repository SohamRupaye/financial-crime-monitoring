package com.sohamrupaye.financialcrimemonitoring.controller;

import com.sohamrupaye.financialcrimemonitoring.dto.CreateCustomerRequest;
import com.sohamrupaye.financialcrimemonitoring.dto.CustomerResponse;
import com.sohamrupaye.financialcrimemonitoring.model.enums.RiskLevel;
import com.sohamrupaye.financialcrimemonitoring.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * HTTP entry point for customers.
 *
 * <p>A controller should be boring: accept a request, hand it to a service,
 * return the result. No business logic and no repository — a controller that
 * injects one has skipped a layer.
 *
 * <p>Versioned from day one. Adding a version once clients exist is far harder
 * than carrying one from the start.
 */
@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    /**
     * {@code GET /api/v1/customers?page=0&size=20&sort=lastName,asc}
     *
     * <p>Always paginated — an unbounded {@code findAll} over a
     * transaction-scale table is how an API takes down its own database.
     *
     * <p>An unknown {@code riskLevel} becomes a 400 through the type-mismatch
     * handler in {@code GlobalExceptionHandler}, which had to be added
     * explicitly: the default is an unhelpful 500.
     */
    @GetMapping
    public Page<CustomerResponse> list(
            @RequestParam(required = false) RiskLevel riskLevel,
            @PageableDefault(size = 20, sort = "lastName", direction = Sort.Direction.ASC) Pageable pageable) {

        return riskLevel == null
                ? customerService.findAll(pageable)
                : customerService.findByRiskLevel(riskLevel, pageable);
    }

    /**
     * {@code GET /api/v1/customers/CUST-3F2A9C41}
     *
     * <p>No try/catch for the missing case: the service throws and
     * {@code GlobalExceptionHandler} renders the 404.
     */
    @GetMapping("/{customerReference}")
    public CustomerResponse get(@PathVariable String customerReference) {
        return customerService.findByReference(customerReference);
    }

    /**
     * {@code POST /api/v1/customers}
     *
     * <p>{@code @Valid} is what triggers the constraints on
     * {@link CreateCustomerRequest}; drop it and invalid payloads sail through.
     * {@code ResponseEntity} rather than {@code @ResponseStatus} because a
     * {@code Location} header is needed.
     */
    @PostMapping
    public ResponseEntity<CustomerResponse> create(
            @Valid @RequestBody CreateCustomerRequest request,
            UriComponentsBuilder uriBuilder) {

        CustomerResponse created = customerService.create(request);

        URI location = uriBuilder
                .path("/api/v1/customers/{customerReference}")
                .buildAndExpand(created.customerReference())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }
}
