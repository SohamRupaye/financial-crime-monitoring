package com.sohamrupaye.financialcrimemonitoring.repository;

import com.sohamrupaye.financialcrimemonitoring.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository
        extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    /**
     * The entity graph is what keeps {@code TransactionMapper} from firing two
     * extra selects for the lazy account and customer.
     */
    @EntityGraph(attributePaths = {"account", "account.customer"})
    Optional<Transaction> findByTransactionReference(String transactionReference);

    boolean existsByTransactionReference(String transactionReference);

    /**
     * Redeclared purely to attach the entity graph. Without it, a page of 20
     * transactions costs 41 queries once the mapper reaches for each account and
     * customer.
     */
    @Override
    @EntityGraph(attributePaths = {"account", "account.customer"})
    Page<Transaction> findAll(Specification<Transaction> specification, Pageable pageable);

    /**
     * One account's activity inside a time window, newest first. This is the only
     * query the AML rules need, and it is what
     * {@code idx_transactions_account_occurred_at} exists for.
     *
     * <p>The interval is half-open — {@code [from, until)}. Excluding the upper
     * bound is what stops a transaction from finding itself when the engine asks
     * for everything that preceded it.
     */
    @Query("""
            SELECT t FROM Transaction t
            WHERE t.account.id = :accountId
              AND t.occurredAt >= :from
              AND t.occurredAt < :until
            ORDER BY t.occurredAt DESC
            """)
    List<Transaction> findWindow(@Param("accountId") Long accountId,
                                 @Param("from") Instant from,
                                 @Param("until") Instant until);
}
