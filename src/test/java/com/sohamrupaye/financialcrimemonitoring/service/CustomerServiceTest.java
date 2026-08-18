package com.sohamrupaye.financialcrimemonitoring.service;

import com.sohamrupaye.financialcrimemonitoring.dto.CreateCustomerRequest;
import com.sohamrupaye.financialcrimemonitoring.dto.CustomerResponse;
import com.sohamrupaye.financialcrimemonitoring.exception.DuplicateResourceException;
import com.sohamrupaye.financialcrimemonitoring.exception.ResourceNotFoundException;
import com.sohamrupaye.financialcrimemonitoring.model.Customer;
import com.sohamrupaye.financialcrimemonitoring.model.enums.RiskLevel;
import com.sohamrupaye.financialcrimemonitoring.repository.CustomerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test — layer 1 of 3.
 *
 * <p>No Spring context at all. {@code MockitoExtension} wires a mock repository
 * into the service, so this exercises business rules in milliseconds with no
 * database and no Docker. Most of your tests should look like this.
 *
 * <p>Note there is no {@code @SpringBootTest} here. Starting a full context to
 * test an if-statement is the most common way Spring test suites become too slow
 * to run on every save.
 */
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerService customerService;

    private static CreateCustomerRequest sampleRequest() {
        return new CreateCustomerRequest(
                "  Asha  ",
                "Menon",
                "Asha.Menon@Example.COM",
                LocalDate.of(1990, 5, 17),
                "IN");
    }

    @Test
    @DisplayName("create() assigns a server-generated reference and LOW risk")
    void createAssignsReferenceAndDefaultRisk() {
        when(customerRepository.existsByEmail("asha.menon@example.com")).thenReturn(false);
        // Echo back whatever was saved, as a real repository would.
        when(customerRepository.save(any(Customer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CustomerResponse response = customerService.create(sampleRequest());

        assertThat(response.customerReference()).startsWith("CUST-");
        // Risk is server-assigned, never client-supplied.
        assertThat(response.riskLevel()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    @DisplayName("create() normalises email to lowercase and trims names")
    void createNormalisesInput() {
        when(customerRepository.existsByEmail("asha.menon@example.com")).thenReturn(false);
        when(customerRepository.save(any(Customer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        customerService.create(sampleRequest());

        // ArgumentCaptor inspects what the service actually handed the repository,
        // which is how you assert on a collaborator's input rather than a return.
        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());

        Customer saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("asha.menon@example.com");
        assertThat(saved.getFirstName()).isEqualTo("Asha");
        assertThat(saved.getCountryCode()).isEqualTo("IN");
    }

    @Test
    @DisplayName("create() rejects a duplicate email and does not save")
    void createRejectsDuplicateEmail() {
        when(customerRepository.existsByEmail("asha.menon@example.com")).thenReturn(true);

        assertThatThrownBy(() -> customerService.create(sampleRequest()))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already exists");

        // Asserting the absence of the write matters as much as the exception.
        verify(customerRepository, never()).save(any());
    }

    @Test
    @DisplayName("findByReference() throws when the customer is absent")
    void findByReferenceThrowsWhenMissing() {
        when(customerRepository.findByCustomerReference("CUST-NOPE"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.findByReference("CUST-NOPE"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("CUST-NOPE");
    }
}
