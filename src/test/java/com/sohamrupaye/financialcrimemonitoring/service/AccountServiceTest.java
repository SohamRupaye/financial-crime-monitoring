package com.sohamrupaye.financialcrimemonitoring.service;

import com.sohamrupaye.financialcrimemonitoring.dto.AccountResponse;
import com.sohamrupaye.financialcrimemonitoring.dto.CreateAccountRequest;
import com.sohamrupaye.financialcrimemonitoring.exception.ResourceNotFoundException;
import com.sohamrupaye.financialcrimemonitoring.model.Account;
import com.sohamrupaye.financialcrimemonitoring.model.Customer;
import com.sohamrupaye.financialcrimemonitoring.model.enums.AccountStatus;
import com.sohamrupaye.financialcrimemonitoring.model.enums.AccountType;
import com.sohamrupaye.financialcrimemonitoring.model.enums.RiskLevel;
import com.sohamrupaye.financialcrimemonitoring.repository.AccountRepository;
import com.sohamrupaye.financialcrimemonitoring.repository.CustomerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final String CUSTOMER_REFERENCE = "CUST-3F2A9C41";

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private AccountService accountService;

    private static Customer customer() {
        return new Customer(CUSTOMER_REFERENCE, "Asha", "Menon", "asha.menon@example.com",
                LocalDate.of(1990, 5, 17), "IN", RiskLevel.MEDIUM);
    }

    @Test
    @DisplayName("create() opens the account at zero balance and ACTIVE status")
    void createAppliesServerSideDefaults() {
        when(customerRepository.findByCustomerReference(CUSTOMER_REFERENCE))
                .thenReturn(Optional.of(customer()));
        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AccountResponse response = accountService.create(
                CUSTOMER_REFERENCE, new CreateAccountRequest(AccountType.SAVINGS, "INR"));

        assertThat(response.accountNumber()).startsWith("ACC-");
        assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(response.customerReference()).isEqualTo(CUSTOMER_REFERENCE);
        // compareTo, not isEqualTo: BigDecimal.ZERO and 0.0000 differ by equals.
        assertThat(response.balance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("create() normalises the currency code to uppercase")
    void createNormalisesCurrency() {
        when(customerRepository.findByCustomerReference(CUSTOMER_REFERENCE))
                .thenReturn(Optional.of(customer()));
        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        accountService.create(CUSTOMER_REFERENCE,
                new CreateAccountRequest(AccountType.CURRENT, "inr"));

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrency()).isEqualTo("INR");
    }

    @Test
    @DisplayName("create() rejects an unknown customer and writes nothing")
    void createRejectsUnknownCustomer() {
        when(customerRepository.findByCustomerReference("CUST-NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.create(
                "CUST-NOPE", new CreateAccountRequest(AccountType.SAVINGS, "INR")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("CUST-NOPE");

        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("listing accounts for an unknown customer is a 404, not an empty list")
    void listForUnknownCustomerThrows() {
        when(customerRepository.findByCustomerReference("CUST-NOPE")).thenReturn(Optional.empty());

        // An empty list would be indistinguishable from a real customer who has
        // opened no accounts, so the customer lookup has to happen first.
        assertThatThrownBy(() -> accountService.findByCustomerReference("CUST-NOPE"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Customer");

        verifyNoInteractions(accountRepository);
    }

    @Test
    @DisplayName("findByAccountNumber() throws when the account is absent")
    void findByAccountNumberThrowsWhenMissing() {
        when(accountRepository.findByAccountNumber("ACC-MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.findByAccountNumber("ACC-MISSING"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Account");

        verify(customerRepository, never()).findByCustomerReference(anyString());
    }
}
