package com.sohamrupaye.financialcrimemonitoring.controller;

import com.sohamrupaye.financialcrimemonitoring.dto.CreateTransactionRequest;
import com.sohamrupaye.financialcrimemonitoring.dto.TransactionResponse;
import com.sohamrupaye.financialcrimemonitoring.exception.BusinessRuleViolationException;
import com.sohamrupaye.financialcrimemonitoring.exception.ResourceNotFoundException;
import com.sohamrupaye.financialcrimemonitoring.model.enums.TransactionType;
import com.sohamrupaye.financialcrimemonitoring.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
@AutoConfigureMockMvc(addFilters = false)
class TransactionControllerTest {

    private static final String ACCOUNT_NUMBER = "ACC-9B41C7E20D5A";
    private static final String TRANSACTION_REFERENCE = "TXN-93842A1C";
    private static final Instant OCCURRED_AT = Instant.parse("2026-09-01T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TransactionService transactionService;

    private static TransactionResponse sampleResponse() {
        return new TransactionResponse(TRANSACTION_REFERENCE, ACCOUNT_NUMBER, "CUST-3F2A9C41",
                TransactionType.TRANSFER, new BigDecimal("485000.0000"), "INR",
                "ACC-EXTERNAL-8841", "IN", OCCURRED_AT, Instant.parse("2026-09-01T10:00:05Z"));
    }

    private static CreateTransactionRequest validRequest() {
        return new CreateTransactionRequest(ACCOUNT_NUMBER, TransactionType.TRANSFER,
                new BigDecimal("485000.00"), "INR", "ACC-EXTERNAL-8841", "IN", OCCURRED_AT);
    }

    private String json(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    @Test
    @DisplayName("POST returns 201 with a Location header")
    void ingestReturnsCreated() throws Exception {
        when(transactionService.ingest(any(CreateTransactionRequest.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        "http://localhost/api/v1/transactions/" + TRANSACTION_REFERENCE))
                .andExpect(jsonPath("$.transactionReference").value(TRANSACTION_REFERENCE))
                .andExpect(jsonPath("$.customerReference").value("CUST-3F2A9C41"))
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    @Test
    @DisplayName("POST to an unknown account returns 404")
    void ingestUnknownAccountReturnsNotFound() throws Exception {
        when(transactionService.ingest(any(CreateTransactionRequest.class)))
                .thenThrow(ResourceNotFoundException.of("Account", ACCOUNT_NUMBER));

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a broken business rule is 422, not 400 - the request itself was fine")
    void ingestBusinessRuleViolationReturnsUnprocessable() throws Exception {
        when(transactionService.ingest(any(CreateTransactionRequest.class)))
                .thenThrow(new BusinessRuleViolationException(
                        "Account %s is closed and cannot receive transactions"
                                .formatted(ACCOUNT_NUMBER)));

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Request cannot be processed"))
                .andExpect(jsonPath("$.detail").value(
                        "Account ACC-9B41C7E20D5A is closed and cannot receive transactions"));
    }

    @Test
    @DisplayName("POST rejects a negative amount and a future occurrence time")
    void ingestRejectsInvalidFields() throws Exception {
        CreateTransactionRequest invalid = new CreateTransactionRequest(
                ACCOUNT_NUMBER, TransactionType.TRANSFER, new BigDecimal("-1"), "INR",
                null, "IN", Instant.now().plusSeconds(3600));

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").value("amount must be greater than zero"))
                .andExpect(jsonPath("$.errors.occurredAt").value("occurredAt cannot be in the future"));
    }

    @Test
    @DisplayName("POST rejects an amount with more precision than the column holds")
    void ingestRejectsExcessivePrecision() throws Exception {
        CreateTransactionRequest invalid = new CreateTransactionRequest(
                ACCOUNT_NUMBER, TransactionType.TRANSFER, new BigDecimal("1000.123456"), "INR",
                null, "IN", OCCURRED_AT);

        // Without @Digits this would be silently rounded to 4 places on insert.
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());
    }

    @Test
    @DisplayName("GET by reference returns the transaction")
    void getReturnsTransaction() throws Exception {
        when(transactionService.findByReference(TRANSACTION_REFERENCE))
                .thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/transactions/{ref}", TRANSACTION_REFERENCE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occurredAt").value("2026-09-01T10:00:00Z"));
    }

    @Test
    @DisplayName("GET passes the filters through and defaults to newest first")
    void searchPassesFiltersToService() throws Exception {
        when(transactionService.search(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleResponse()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/transactions")
                        .param("accountNumber", ACCOUNT_NUMBER)
                        .param("transactionType", "CASH_DEPOSIT")
                        .param("minAmount", "500000")
                        .param("from", "2026-09-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].transactionReference")
                        .value(TRANSACTION_REFERENCE));

        verify(transactionService).search(
                eq(ACCOUNT_NUMBER),
                eq(TransactionType.CASH_DEPOSIT),
                eq(new BigDecimal("500000")),
                eq(Instant.parse("2026-09-01T00:00:00Z")),
                isNull(),
                any(Pageable.class));
    }

    @Test
    @DisplayName("GET with an unparseable transaction type returns 400")
    void searchWithInvalidTypeReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/transactions").param("transactionType", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid parameter"));
    }
}
