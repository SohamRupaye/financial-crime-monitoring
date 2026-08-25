package com.sohamrupaye.financialcrimemonitoring.rules;

import com.sohamrupaye.financialcrimemonitoring.model.Transaction;
import com.sohamrupaye.financialcrimemonitoring.repository.TransactionRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * The one place the rules package touches persistence.
 *
 * <p>Keeping the adapter here rather than injecting the repository into each rule
 * is what lets every rule be tested with a two-line stub.
 */
@Component
public class JpaTransactionHistory implements TransactionHistory {

    private final TransactionRepository transactionRepository;

    public JpaTransactionHistory(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Override
    public List<Transaction> before(Transaction transaction, Duration window) {
        return transactionRepository.findWindow(
                transaction.getAccount().getId(),
                transaction.getOccurredAt().minus(window),
                transaction.getOccurredAt());
    }
}
