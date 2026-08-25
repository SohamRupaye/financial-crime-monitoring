package com.sohamrupaye.financialcrimemonitoring.rules;

/**
 * One independent AML check.
 *
 * <p>Implementations are Spring beans; the engine receives all of them as a
 * {@code List<AmlRule>} and never names one individually. Adding a rule is a new
 * class and its tests — no existing file changes, which is the entire point of
 * the strategy pattern here.
 *
 * <p>A rule must be free of side effects and must not write anything. It answers
 * a question about the context it is handed and nothing else.
 */
public interface AmlRule {

    RuleCode code();

    RuleResult evaluate(RuleContext context);
}
