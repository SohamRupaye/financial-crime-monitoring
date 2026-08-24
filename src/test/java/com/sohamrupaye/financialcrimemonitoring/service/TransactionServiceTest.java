package com.sohamrupaye.financialcrimemonitoring.service;

import com.sohamrupaye.financialcrimemonitoring.dto.CreateTransactionRequest;
import com.sohamrupaye.financialcrimemonitoring.dto.TransactionResponse;
import com.sohamrupaye.financialcrimemonitoring.exception.BusinessRuleViolationException;
import com.sohamrupaye.financialcrimemonitoring.exception.ResourceNotFoundException;
import com.sohamrupaye.financialcrimemonitoring.model.Account;
import com.sohamrupaye.financialcrimemonitoring.model.Customer;
import com.sohamrupaye.financialcrimemonitoring.model.Transaction;
import com.sohamrupaye.financialcrimemonitoring.model.enums.AccountStatus;
import com.sohamrupaye.financialcrimemonitoring.model.enums.AccountType;
import com.sohamrupaye.financialcrimemonitoring.model.enums.RiskLevel;
import com.sohamrupaye.financialcrimemonitoring.model.enums.TransactionType;
import com.sohamrupaye.financialcrimemonitoring.repository.AccountRepository;
import com.sohamrupaye.financialcrimemonitoring.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final String ACCOUNT_NUMBER = "ACC-9B41C7E20D5A";
    private static final Instant OCCURRED_AT = Instant.parse("2026-09-01T10:00:00Z");

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private TransactionService transactionService;

    private static Account account(String currency, AccountStatus status) {
        Customer customer = new Customer("CUST-3F2A9C41", "Asha", "Menon",
                "asha.menon@example.com", LocalDate.of(1990, 5, 17), "IN", RiskLevel.MEDIUM);

        return new Account(ACCOUNT_NUMBER, customer, currency, BigDecimal.ZERO,
                LocalDate.of(2026, 1, 10), AccountType.SAVINGS, status);
    }

    private static CreateTransactionRequest request(String currency, String country) {
        return new CreateTransactionRequest(ACCOUNT_NUMBER, TransactionType.TRANSFER,
                new BigDecimal("485000.00"), currency, "ACC-EXTERNAL-8841", country, OCCURRED_AT);
    }

    private void givenAccount(Account account) {
        when(accountRepository.findByAccountNumber(ACCOUNT_NUMBER)).thenReturn(Optional.of(account));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("ingest() assigns a reference and keeps the reported occurrence time")
    void ingestAssignsReference() {
        givenAccount(account("INR", AccountStatus.ACTIVE));

        TransactionResponse response = transactionService.ingest(request("INR", "IN"));

        assertThat(response.transactionReference()).startsWith("TXN-");
        assertThat(response.accountNumber()).isEqualTo(ACCOUNT_NUMBER);
        assertThat(response.customerReference()).isEqualTo("CUST-3F2A9C41");
        // Not overwritten with "now" - the reported time is the one the rules use.
        assertThat(response.occurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(response.amount()).isEqualByComparingTo("485000.00");
    }

    @Test
    @DisplayName("ingest() normalises currency and country to uppercase")
    void ingestNormalisesCodes() {
        givenAccount(account("INR", AccountStatus.ACTIVE));

        transactionService.ingest(request("inr", "in"));

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());

        assertThat(captor.getValue().getCurrency()).isEqualTo("INR");
        assertThat(captor.getValue().getCounterpartyCountry()).isEqualTo("IN");
    }

    @Test
    @DisplayName("ingest() rejects an unknown account and writes nothing")
    void ingestRejectsUnknownAccount() {
        when(accountRepository.findByAccountNumber(ACCOUNT_NUMBER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.ingest(request("INR", "IN")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(ACCOUNT_NUMBER);

        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("ingest() refuses to post to a closed account")
    void ingestRejectsClosedAccount() {
        when(accountRepository.findByAccountNumber(ACCOUNT_NUMBER))
                .thenReturn(Optional.of(account("INR", AccountStatus.CLOSED)));

        assertThatThrownBy(() -> transactionService.ingest(request("INR", "IN")))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("closed");

        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("ingest() still records activity on a frozen account")
    void ingestAllowsFrozenAccount() {
        givenAccount(account("INR", AccountStatus.FROZEN));

        // Deliberate: an attempted movement on a frozen account is exactly the
        // kind of thing monitoring exists to catch, so it must not be dropped.
        TransactionResponse response = transactionService.ingest(request("INR", "IN"));

        assertThat(response.transactionReference()).startsWith("TXN-");
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    @DisplayName("ingest() refuses a currency that differs from the account's")
    void ingestRejectsCurrencyMismatch() {
        when(accountRepository.findByAccountNumber(ACCOUNT_NUMBER))
                .thenReturn(Optional.of(account("INR", AccountStatus.ACTIVE)));

        // No FX conversion exists, so a USD amount compared against an INR
        // threshold would be meaningless rather than merely imprecise.
        assertThatThrownBy(() -> transactionService.ingest(request("USD", "US")))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("does not match account currency");

        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("findByReference() throws when the transaction is absent")
    void findByReferenceThrowsWhenMissing() {
        when(transactionRepository.findByTransactionReference("TXN-NOPE"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.findByReference("TXN-NOPE"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Transaction");
    }
}
