package com.sohamrupaye.financialcrimemonitoring.repository;

import com.sohamrupaye.financialcrimemonitoring.TestcontainersConfiguration;
import com.sohamrupaye.financialcrimemonitoring.config.JpaAuditingConfig;
import com.sohamrupaye.financialcrimemonitoring.model.Account;
import com.sohamrupaye.financialcrimemonitoring.model.Customer;
import com.sohamrupaye.financialcrimemonitoring.model.enums.AccountStatus;
import com.sohamrupaye.financialcrimemonitoring.model.enums.AccountType;
import com.sohamrupaye.financialcrimemonitoring.model.enums.RiskLevel;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class AccountRepositoryTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private EntityManager entityManager;

    private Customer asha;
    private Customer ravi;

    @BeforeEach
    void persistCustomers() {
        asha = customerRepository.save(new Customer("CUST-A0000001", "Asha", "Menon",
                "asha@example.com", LocalDate.of(1990, 1, 1), "IN", RiskLevel.LOW));
        ravi = customerRepository.save(new Customer("CUST-B0000002", "Ravi", "Iyer",
                "ravi@example.com", LocalDate.of(1985, 3, 9), "IN", RiskLevel.HIGH));
    }

    private Account account(String number, Customer owner, BigDecimal balance) {
        return new Account(number, owner, "INR", balance, LocalDate.now(),
                AccountType.SAVINGS, AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("an account round-trips with its enums stored as strings")
    void saveAndFind() {
        accountRepository.save(account("ACC-000000000001", asha, BigDecimal.ZERO));

        Optional<Account> found = accountRepository.findByAccountNumber("ACC-000000000001");

        assertThat(found).isPresent();
        assertThat(found.get().getAccountType()).isEqualTo(AccountType.SAVINGS);
        assertThat(found.get().getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("NUMERIC(19,4) keeps four decimal places through a real round-trip")
    void balancePrecisionSurvivesTheDatabase() {
        accountRepository.save(account("ACC-000000000002", asha, new BigDecimal("12345.6789")));

        // Clearing the persistence context forces a genuine SELECT. Without it the
        // entity comes straight back from memory and proves nothing about the column.
        entityManager.flush();
        entityManager.clear();

        BigDecimal balance = accountRepository.findByAccountNumber("ACC-000000000002")
                .orElseThrow()
                .getBalance();

        assertThat(balance).isEqualByComparingTo("12345.6789");
        assertThat(balance.scale()).isEqualTo(4);
    }

    @Test
    @DisplayName("the nested property query returns only the given customer's accounts")
    void findByCustomerReference() {
        accountRepository.save(account("ACC-000000000003", asha, BigDecimal.ZERO));
        accountRepository.save(account("ACC-000000000004", asha, BigDecimal.ZERO));
        accountRepository.save(account("ACC-000000000005", ravi, BigDecimal.ZERO));

        List<Account> found =
                accountRepository.findByCustomer_CustomerReference("CUST-A0000001");

        assertThat(found)
                .hasSize(2)
                .extracting(Account::getAccountNumber)
                .containsExactlyInAnyOrder("ACC-000000000003", "ACC-000000000004");
    }

    @Test
    @DisplayName("an unknown customer reference yields an empty list, not an error")
    void findByUnknownCustomerReference() {
        assertThat(accountRepository.findByCustomer_CustomerReference("CUST-NOPE")).isEmpty();
    }

    @Test
    @DisplayName("existsByAccountNumber() backs the uniqueness check")
    void existsByAccountNumber() {
        accountRepository.save(account("ACC-000000000006", ravi, BigDecimal.ZERO));

        assertThat(accountRepository.existsByAccountNumber("ACC-000000000006")).isTrue();
        assertThat(accountRepository.existsByAccountNumber("ACC-000000000007")).isFalse();
    }
}
