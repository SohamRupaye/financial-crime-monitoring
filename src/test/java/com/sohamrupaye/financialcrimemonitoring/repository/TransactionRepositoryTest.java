package com.sohamrupaye.financialcrimemonitoring.repository;

import com.sohamrupaye.financialcrimemonitoring.TestcontainersConfiguration;
import com.sohamrupaye.financialcrimemonitoring.config.JpaAuditingConfig;
import com.sohamrupaye.financialcrimemonitoring.model.Account;
import com.sohamrupaye.financialcrimemonitoring.model.Customer;
import com.sohamrupaye.financialcrimemonitoring.model.Transaction;
import com.sohamrupaye.financialcrimemonitoring.model.enums.AccountStatus;
import com.sohamrupaye.financialcrimemonitoring.model.enums.AccountType;
import com.sohamrupaye.financialcrimemonitoring.model.enums.RiskLevel;
import com.sohamrupaye.financialcrimemonitoring.model.enums.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class TransactionRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-09-01T10:00:00Z");
    private static final Pageable FIRST_PAGE = PageRequest.of(0, 20);

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CustomerRepository customerRepository;

    private Account account;
    private Account otherAccount;

    @BeforeEach
    void persistAccounts() {
        Customer asha = customerRepository.save(new Customer("CUST-A0000001", "Asha", "Menon",
                "asha@example.com", LocalDate.of(1990, 1, 1), "IN", RiskLevel.LOW));

        account = accountRepository.save(new Account("ACC-000000000001", asha, "INR",
                BigDecimal.ZERO, LocalDate.now(), AccountType.SAVINGS, AccountStatus.ACTIVE));
        otherAccount = accountRepository.save(new Account("ACC-000000000002", asha, "INR",
                BigDecimal.ZERO, LocalDate.now(), AccountType.CURRENT, AccountStatus.ACTIVE));
    }

    private Page<Transaction> search(String accountNumber, TransactionType transactionType,
                                     BigDecimal minAmount, Instant from, Instant until) {

        return transactionRepository.findAll(TransactionSpecifications.matching(
                accountNumber, transactionType, minAmount, from, until), FIRST_PAGE);
    }

    private Transaction save(Account on, String reference, String amount, Instant occurredAt) {
        return save(on, reference, amount, occurredAt, TransactionType.TRANSFER, "IN");
    }

    private Transaction save(Account on, String reference, String amount, Instant occurredAt,
                             TransactionType type, String country) {
        return transactionRepository.save(new Transaction(reference, on, type,
                new BigDecimal(amount), "INR", "ACC-EXTERNAL", country, occurredAt));
    }

    @Test
    @DisplayName("a transaction round-trips with its amount and type intact")
    void saveAndFindByReference() {
        save(account, "TXN-00000001", "485000.5000", NOW);

        Transaction found = transactionRepository.findByTransactionReference("TXN-00000001")
                .orElseThrow();

        assertThat(found.getAmount()).isEqualByComparingTo("485000.5000");
        assertThat(found.getTransactionType()).isEqualTo(TransactionType.TRANSFER);
        // The entity graph should have brought both of these along already.
        assertThat(found.getAccount().getAccountNumber()).isEqualTo("ACC-000000000001");
        assertThat(found.getAccount().getCustomer().getCustomerReference())
                .isEqualTo("CUST-A0000001");
    }

    @Test
    @DisplayName("findWindow() includes the lower bound and excludes the upper")
    void findWindowIsHalfOpen() {
        save(account, "TXN-00000002", "1000", NOW.minus(Duration.ofMinutes(20)));
        save(account, "TXN-00000003", "1000", NOW.minus(Duration.ofMinutes(10)));
        save(account, "TXN-00000004", "1000", NOW.minus(Duration.ofMinutes(5)));
        save(account, "TXN-00000005", "1000", NOW);

        List<Transaction> window = transactionRepository.findWindow(
                account.getId(), NOW.minus(Duration.ofMinutes(10)), NOW);

        // The one exactly at `from` is in; the one exactly at `until` is out, which
        // is what stops a transaction finding itself.
        assertThat(window)
                .extracting(Transaction::getTransactionReference)
                .containsExactly("TXN-00000004", "TXN-00000003");
    }

    @Test
    @DisplayName("findWindow() is scoped to one account")
    void findWindowIsScopedToAccount() {
        save(account, "TXN-00000006", "1000", NOW.minus(Duration.ofMinutes(1)));
        save(otherAccount, "TXN-00000007", "1000", NOW.minus(Duration.ofMinutes(1)));

        List<Transaction> window = transactionRepository.findWindow(
                account.getId(), NOW.minus(Duration.ofHours(1)), NOW);

        assertThat(window)
                .extracting(Transaction::getTransactionReference)
                .containsExactly("TXN-00000006");
    }

    @Test
    @DisplayName("search() with every filter null returns everything")
    void searchWithoutFilters() {
        save(account, "TXN-00000008", "1000", NOW);
        save(otherAccount, "TXN-00000009", "2000", NOW);

        Page<Transaction> page =
                search(null, null, null, null, null);

        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("search() filters by account number through the relationship")
    void searchByAccountNumber() {
        save(account, "TXN-00000010", "1000", NOW);
        save(otherAccount, "TXN-00000011", "1000", NOW);

        Page<Transaction> page = search(
                "ACC-000000000001", null, null, null, null);

        assertThat(page.getContent())
                .extracting(Transaction::getTransactionReference)
                .containsExactly("TXN-00000010");
    }

    @Test
    @DisplayName("search() filters by type and minimum amount together")
    void searchByTypeAndAmount() {
        save(account, "TXN-00000012", "9000", NOW, TransactionType.CASH_DEPOSIT, "IN");
        save(account, "TXN-00000013", "600000", NOW, TransactionType.CASH_DEPOSIT, "IN");
        save(account, "TXN-00000014", "600000", NOW, TransactionType.TRANSFER, "IN");

        Page<Transaction> page = search(
                null, TransactionType.CASH_DEPOSIT, new BigDecimal("500000"),
                null, null);

        assertThat(page.getContent())
                .extracting(Transaction::getTransactionReference)
                .containsExactly("TXN-00000013");
    }

    @Test
    @DisplayName("search() applies the date range as a half-open interval")
    void searchByDateRange() {
        save(account, "TXN-00000015", "1000", NOW.minus(Duration.ofDays(2)));
        save(account, "TXN-00000016", "1000", NOW.minus(Duration.ofDays(1)));
        save(account, "TXN-00000017", "1000", NOW);

        Page<Transaction> page = search(
                null, null, null, NOW.minus(Duration.ofDays(1)), NOW);

        assertThat(page.getContent())
                .extracting(Transaction::getTransactionReference)
                .containsExactly("TXN-00000016");
    }
}
