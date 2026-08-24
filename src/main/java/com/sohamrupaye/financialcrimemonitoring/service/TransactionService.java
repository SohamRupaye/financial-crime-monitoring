package com.sohamrupaye.financialcrimemonitoring.service;

import com.sohamrupaye.financialcrimemonitoring.dto.CreateTransactionRequest;
import com.sohamrupaye.financialcrimemonitoring.dto.TransactionResponse;
import com.sohamrupaye.financialcrimemonitoring.exception.BusinessRuleViolationException;
import com.sohamrupaye.financialcrimemonitoring.exception.ResourceNotFoundException;
import com.sohamrupaye.financialcrimemonitoring.mapper.TransactionMapper;
import com.sohamrupaye.financialcrimemonitoring.model.Account;
import com.sohamrupaye.financialcrimemonitoring.model.Transaction;
import com.sohamrupaye.financialcrimemonitoring.model.enums.AccountStatus;
import com.sohamrupaye.financialcrimemonitoring.model.enums.TransactionType;
import com.sohamrupaye.financialcrimemonitoring.repository.AccountRepository;
import com.sohamrupaye.financialcrimemonitoring.repository.TransactionRepository;
import com.sohamrupaye.financialcrimemonitoring.repository.TransactionSpecifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Ingestion and retrieval of transactions.
 */
@Service
@Transactional(readOnly = true)
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private static final String REFERENCE_PREFIX = "TXN-";

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    public TransactionService(TransactionRepository transactionRepository,
                              AccountRepository accountRepository) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
    }

    /**
     * Records a transaction against an existing account.
     *
     * <p>This is an ingestion endpoint, not a payment endpoint — it records that
     * money moved elsewhere, so it does not touch balances. Scoring is separate
     * and gets wired in once the rules engine exists.
     */
    @Transactional
    public TransactionResponse ingest(CreateTransactionRequest request) {
        Account account = accountRepository.findByAccountNumber(request.accountNumber())
                .orElseThrow(() -> ResourceNotFoundException.of("Account", request.accountNumber()));

        String currency = request.currency().toUpperCase(Locale.ROOT);
        requirePostable(account, currency);

        Transaction transaction = new Transaction(
                generateReference(),
                account,
                request.transactionType(),
                request.amount(),
                currency,
                request.counterpartyAccountNumber(),
                request.counterpartyCountry().toUpperCase(Locale.ROOT),
                request.occurredAt());

        Transaction saved = transactionRepository.save(transaction);
        log.info("Ingested transaction {} on account {} for {} {}",
                saved.getTransactionReference(), account.getAccountNumber(),
                saved.getAmount(), saved.getCurrency());

        return TransactionMapper.toResponse(saved);
    }

    public TransactionResponse findByReference(String transactionReference) {
        return transactionRepository.findByTransactionReference(transactionReference)
                .map(TransactionMapper::toResponse)
                .orElseThrow(() -> ResourceNotFoundException.of("Transaction", transactionReference));
    }

    public Page<TransactionResponse> search(String accountNumber,
                                            TransactionType transactionType,
                                            BigDecimal minAmount,
                                            Instant from,
                                            Instant until,
                                            Pageable pageable) {

        return transactionRepository
                .findAll(TransactionSpecifications.matching(
                        accountNumber, transactionType, minAmount, from, until), pageable)
                .map(TransactionMapper::toResponse);
    }

    /**
     * Rules Bean Validation cannot check, because they need the account.
     *
     * <p>A CLOSED account cannot receive postings at all. A FROZEN one deliberately
     * can: an attempted movement on a frozen account is exactly what a monitoring
     * system exists to see, so refusing to record it would throw the signal away.
     *
     * <p>Currency has to match the account. A cross-currency posting would need FX
     * conversion before any threshold comparison meant anything, and that is not
     * something to fake.
     */
    private void requirePostable(Account account, String currency) {
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new BusinessRuleViolationException(
                    "Account %s is closed and cannot receive transactions"
                            .formatted(account.getAccountNumber()));
        }

        if (!account.getCurrency().equals(currency)) {
            throw new BusinessRuleViolationException(
                    "Transaction currency %s does not match account currency %s"
                            .formatted(currency, account.getCurrency()));
        }
    }

    private String generateReference() {
        return REFERENCE_PREFIX + UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase(Locale.ROOT);
    }
}
