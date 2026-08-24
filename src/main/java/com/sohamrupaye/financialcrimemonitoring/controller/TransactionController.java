package com.sohamrupaye.financialcrimemonitoring.controller;

import com.sohamrupaye.financialcrimemonitoring.dto.CreateTransactionRequest;
import com.sohamrupaye.financialcrimemonitoring.dto.TransactionResponse;
import com.sohamrupaye.financialcrimemonitoring.model.enums.TransactionType;
import com.sohamrupaye.financialcrimemonitoring.service.TransactionService;
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

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    /** {@code POST /api/v1/transactions} */
    @PostMapping
    public ResponseEntity<TransactionResponse> ingest(
            @Valid @RequestBody CreateTransactionRequest request,
            UriComponentsBuilder uriBuilder) {

        TransactionResponse created = transactionService.ingest(request);

        URI location = uriBuilder
                .path("/api/v1/transactions/{transactionReference}")
                .buildAndExpand(created.transactionReference())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }

    /**
     * {@code GET /api/v1/transactions?accountNumber=ACC-...&minAmount=100000&size=20}
     *
     * <p>Every filter is optional. Sorted newest-first by default, because that is
     * how anyone investigating an account actually reads it.
     */
    @GetMapping
    public Page<TransactionResponse> search(
            @RequestParam(required = false) String accountNumber,
            @RequestParam(required = false) TransactionType transactionType,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant until,
            @PageableDefault(size = 20, sort = "occurredAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        return transactionService.search(
                accountNumber, transactionType, minAmount, from, until, pageable);
    }

    /** {@code GET /api/v1/transactions/TXN-93842A1C} */
    @GetMapping("/{transactionReference}")
    public TransactionResponse get(@PathVariable String transactionReference) {
        return transactionService.findByReference(transactionReference);
    }
}
