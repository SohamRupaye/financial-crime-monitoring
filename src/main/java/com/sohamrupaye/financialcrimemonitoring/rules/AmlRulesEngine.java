package com.sohamrupaye.financialcrimemonitoring.rules;

import com.sohamrupaye.financialcrimemonitoring.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Runs every rule against one transaction and returns what each concluded.
 *
 * <p>Scoring is somebody else's job. This class only collects evidence, which is
 * why it can stay this small.
 */
@Service
public class AmlRulesEngine {

    private static final Logger log = LoggerFactory.getLogger(AmlRulesEngine.class);

    private final List<AmlRule> rules;
    private final TransactionHistory history;

    /**
     * Spring supplies every {@link AmlRule} bean on the classpath.
     *
     * <p>Both guards below exist because the failure modes are silent. An empty
     * rule list would score every transaction zero and raise no alerts, and a
     * duplicated code would double-count one rule while making the stored results
     * ambiguous. Neither should wait for someone to notice in production.
     */
    public AmlRulesEngine(List<AmlRule> rules, TransactionHistory history) {
        if (rules.isEmpty()) {
            throw new IllegalStateException("no AML rules found - every transaction would score 0");
        }

        Set<RuleCode> codes = EnumSet.noneOf(RuleCode.class);
        rules.forEach(rule -> {
            if (!codes.add(rule.code())) {
                throw new IllegalStateException("duplicate rule code: " + rule.code());
            }
        });

        this.rules = List.copyOf(rules);
        this.history = history;

        log.info("AML rules engine started with {} rules: {}", rules.size(), codes);
    }

    /**
     * Evaluates the transaction against all rules.
     *
     * <p>Results come back sorted by {@link RuleCode} rather than in bean order,
     * which Spring does not guarantee. Without that, the reasons attached to an
     * assessment would shuffle between runs and the tests would be flaky.
     *
     * <p>Must be called inside a transaction: the context resolves the customer
     * through two lazy associations, and the rules query history.
     */
    public List<RuleResult> evaluate(Transaction transaction) {
        RuleContext context = new RuleContext(
                transaction,
                transaction.getAccount().getCustomer(),
                history);

        return rules.stream()
                .map(rule -> rule.evaluate(context))
                .sorted(Comparator.comparing(RuleResult::code))
                .toList();
    }
}
