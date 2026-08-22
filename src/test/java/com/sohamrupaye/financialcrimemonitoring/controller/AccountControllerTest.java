package com.sohamrupaye.financialcrimemonitoring.controller;

import com.sohamrupaye.financialcrimemonitoring.dto.AccountResponse;
import com.sohamrupaye.financialcrimemonitoring.dto.CreateAccountRequest;
import com.sohamrupaye.financialcrimemonitoring.exception.ResourceNotFoundException;
import com.sohamrupaye.financialcrimemonitoring.model.enums.AccountStatus;
import com.sohamrupaye.financialcrimemonitoring.model.enums.AccountType;
import com.sohamrupaye.financialcrimemonitoring.service.AccountService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
@AutoConfigureMockMvc(addFilters = false)
class AccountControllerTest {

    private static final String CUSTOMER_REFERENCE = "CUST-3F2A9C41";
    private static final String ACCOUNT_NUMBER = "ACC-9B41C7E20D5A";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AccountService accountService;

    private static AccountResponse sampleResponse() {
        return new AccountResponse(ACCOUNT_NUMBER, CUSTOMER_REFERENCE, AccountType.SAVINGS,
                "INR", new BigDecimal("0.0000"), AccountStatus.ACTIVE,
                LocalDate.of(2026, 9, 3), Instant.parse("2026-09-03T10:15:30Z"));
    }

    @Test
    @DisplayName("POST returns 201 with a Location header for the account itself")
    void openReturnsCreated() throws Exception {
        when(accountService.create(eq(CUSTOMER_REFERENCE), any(CreateAccountRequest.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/customers/{ref}/accounts", CUSTOMER_REFERENCE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateAccountRequest(AccountType.SAVINGS, "INR"))))
                .andExpect(status().isCreated())
                // The nested path created it; the canonical path is where it lives.
                .andExpect(header().string("Location",
                        "http://localhost/api/v1/accounts/" + ACCOUNT_NUMBER))
                .andExpect(jsonPath("$.accountNumber").value(ACCOUNT_NUMBER))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST to an unknown customer returns 404, not 500")
    void openForUnknownCustomerReturnsNotFound() throws Exception {
        when(accountService.create(eq("CUST-NOPE"), any(CreateAccountRequest.class)))
                .thenThrow(ResourceNotFoundException.of("Customer", "CUST-NOPE"));

        mockMvc.perform(post("/api/v1/customers/{ref}/accounts", "CUST-NOPE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateAccountRequest(AccountType.SAVINGS, "INR"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Customer not found: CUST-NOPE"));
    }

    @Test
    @DisplayName("POST with a lowercase currency is rejected per field")
    void openRejectsInvalidCurrency() throws Exception {
        mockMvc.perform(post("/api/v1/customers/{ref}/accounts", CUSTOMER_REFERENCE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateAccountRequest(AccountType.SAVINGS, "inr"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.currency").exists());
    }

    @Test
    @DisplayName("POST without an account type is rejected")
    void openRejectsMissingAccountType() throws Exception {
        mockMvc.perform(post("/api/v1/customers/{ref}/accounts", CUSTOMER_REFERENCE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateAccountRequest(null, "INR"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.accountType").value("account type is required"));
    }

    @Test
    @DisplayName("GET by account number exposes the owner as a reference string only")
    void getReturnsAccount() throws Exception {
        when(accountService.findByAccountNumber(ACCOUNT_NUMBER)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/accounts/{number}", ACCOUNT_NUMBER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerReference").value(CUSTOMER_REFERENCE))
                // No nested customer object and no primary key in the payload.
                .andExpect(jsonPath("$.customer").doesNotExist())
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    @Test
    @DisplayName("GET for a missing account returns a 404 ProblemDetail")
    void getMissingAccountReturnsNotFound() throws Exception {
        when(accountService.findByAccountNumber("ACC-MISSING"))
                .thenThrow(ResourceNotFoundException.of("Account", "ACC-MISSING"));

        mockMvc.perform(get("/api/v1/accounts/{number}", "ACC-MISSING"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }

    @Test
    @DisplayName("GET lists the accounts belonging to one customer")
    void listForCustomer() throws Exception {
        when(accountService.findByCustomerReference(CUSTOMER_REFERENCE))
                .thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/customers/{ref}/accounts", CUSTOMER_REFERENCE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].accountNumber").value(ACCOUNT_NUMBER));
    }
    @Test
    @DisplayName("POST with an unknown account type returns 400 listing the valid ones")
    void openRejectsUnknownAccountType() throws Exception {
        // Jackson cannot build the record at all here, so this never reaches
        // validation - it surfaces as an unreadable body and used to be a 500.
        mockMvc.perform(post("/api/v1/customers/{ref}/accounts", CUSTOMER_REFERENCE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountType\":\"GOLD\",\"currency\":\"INR\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"))
                .andExpect(jsonPath("$.detail").value(
                        "'GOLD' is not a valid AccountType. Allowed values: [SAVINGS, CURRENT, BUSINESS]"));
    }

    @Test
    @DisplayName("POST with broken JSON returns 400 without leaking parser internals")
    void openRejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/customers/{ref}/accounts", CUSTOMER_REFERENCE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountType\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "Request body is not valid JSON, or a field has the wrong type"));
    }
}
