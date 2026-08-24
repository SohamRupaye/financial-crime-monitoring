package com.sohamrupaye.financialcrimemonitoring.repository;

import com.sohamrupaye.financialcrimemonitoring.model.Transaction;
import com.sohamrupaye.financialcrimemonitoring.model.enums.TransactionType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the optional-filter predicate for transaction search.
 *
 * <p>The obvious alternative is one JPQL query per filter combination, or a single
 * query guarded with {@code (:param IS NULL OR column = :param)}. The guarded
 * version does not survive contact with PostgreSQL — a parameter used only in
 * {@code ? IS NULL} has no type to infer, and the driver reports "could not
 * determine data type of parameter". Building predicates means the SQL contains
 * only the filters the caller actually supplied, which the query planner also
 * prefers.
 */
public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    public static Specification<Transaction> matching(String accountNumber,
                                                      TransactionType transactionType,
                                                      BigDecimal minAmount,
                                                      Instant from,
                                                      Instant until) {

        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (accountNumber != null) {
                // Walks the relationship; Hibernate turns this into the join.
                predicates.add(builder.equal(
                        root.get("account").get("accountNumber"), accountNumber));
            }
            if (transactionType != null) {
                predicates.add(builder.equal(root.get("transactionType"), transactionType));
            }
            if (minAmount != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("amount"), minAmount));
            }
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("occurredAt"), from));
            }
            if (until != null) {
                // Half-open, matching findWindow: callers pass a date range meaning
                // "up to but not including".
                predicates.add(builder.lessThan(root.get("occurredAt"), until));
            }

            // An empty conjunction is simply true, so no filters means no WHERE.
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
