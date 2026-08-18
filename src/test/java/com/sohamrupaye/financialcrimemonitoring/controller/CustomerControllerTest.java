package com.sohamrupaye.financialcrimemonitoring.controller;

import com.sohamrupaye.financialcrimemonitoring.dto.CreateCustomerRequest;
import com.sohamrupaye.financialcrimemonitoring.dto.CustomerResponse;
import com.sohamrupaye.financialcrimemonitoring.exception.ResourceNotFoundException;
import com.sohamrupaye.financialcrimemonitoring.model.enums.RiskLevel;
import com.sohamrupaye.financialcrimemonitoring.service.CustomerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web slice test — layer 3 of 3.
 *
 * <p>{@code @WebMvcTest} starts only the MVC layer: this controller, JSON
 * serialisation, validation and {@code GlobalExceptionHandler}. There is no
 * database and no service — {@code @MockitoBean} replaces
 * {@link CustomerService} in the context, so what is under test is purely the
 * HTTP contract: status codes, headers, JSON shape, validation.
 *
 * <p>{@code @MockitoBean} is the modern replacement for {@code @MockBean}, which
 * is deprecated for removal. Tutorials written before Spring Boot 3.4 will show
 * the old one.
 *
 * <p>{@code addFilters = false} disables the security filter chain. Without it,
 * every request here would be answered by Spring Security rather than the
 * controller, and these assertions would test the wrong thing.
 */
@WebMvcTest(CustomerController.class)
@AutoConfigureMockMvc(addFilters = false)
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CustomerService customerService;

    private static CustomerResponse sampleResponse() {
        return new CustomerResponse(
                "CUST-3F2A9C41", "Asha", "Menon", "Asha Menon",
                "asha.menon@example.com", LocalDate.of(1990, 5, 17),
                "IN", RiskLevel.LOW, Instant.parse("2026-08-11T10:15:30Z"));
    }

    @Test
    @DisplayName("GET by reference returns 200 with the mapped JSON body")
    void getReturnsCustomer() throws Exception {
        when(customerService.findByReference("CUST-3F2A9C41")).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/customers/CUST-3F2A9C41"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerReference").value("CUST-3F2A9C41"))
                .andExpect(jsonPath("$.fullName").value("Asha Menon"))
                .andExpect(jsonPath("$.riskLevel").value("LOW"))
                // The entity's primary key must never appear in the payload.
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    @Test
    @DisplayName("GET for a missing customer returns a 404 ProblemDetail")
    void getMissingReturnsProblemDetail() throws Exception {
        when(customerService.findByReference("CUST-NOPE"))
                .thenThrow(ResourceNotFoundException.of("Customer", "CUST-NOPE"));

        // Proves GlobalExceptionHandler is wired: the service threw, and the
        // controller itself contains no error handling whatsoever.
        mockMvc.perform(get("/api/v1/customers/CUST-NOPE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.detail").value("Customer not found: CUST-NOPE"));
    }

    @Test
    @DisplayName("POST returns 201 with a Location header")
    void postCreatesCustomer() throws Exception {
        when(customerService.create(any(CreateCustomerRequest.class))).thenReturn(sampleResponse());

        CreateCustomerRequest request = new CreateCustomerRequest(
                "Asha", "Menon", "asha.menon@example.com",
                LocalDate.of(1990, 5, 17), "IN");

        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        "http://localhost/api/v1/customers/CUST-3F2A9C41"))
                .andExpect(jsonPath("$.customerReference").value("CUST-3F2A9C41"));
    }

    @Test
    @DisplayName("GET with an unparseable enum returns 400, not 500")
    void getWithInvalidEnumReturnsBadRequest() throws Exception {
        // Regression test. This returned 500 before GlobalExceptionHandler grew a
        // MethodArgumentTypeMismatchException handler, because the catch-all
        // Exception handler treated a client mistake as a server fault.
        mockMvc.perform(get("/api/v1/customers").param("riskLevel", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid parameter"))
                .andExpect(jsonPath("$.detail").value(
                        "'BOGUS' is not a valid riskLevel. Allowed values: [LOW, MEDIUM, HIGH, CRITICAL]"));
    }

    @Test
    @DisplayName("POST with invalid fields returns 400 listing each field error")
    void postRejectsInvalidPayload() throws Exception {
        // Blank name, malformed email, future date of birth, lowercase country.
        CreateCustomerRequest invalid = new CreateCustomerRequest(
                "", "Menon", "not-an-email", LocalDate.now().plusYears(1), "in");

        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.firstName").value("first name is required"))
                .andExpect(jsonPath("$.errors.email").value("email must be a valid address"))
                .andExpect(jsonPath("$.errors.dateOfBirth").value("date of birth must be in the past"))
                .andExpect(jsonPath("$.errors.countryCode").exists());
    }
}
