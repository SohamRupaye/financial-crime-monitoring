package com.sohamrupaye.financialcrimemonitoring.model.enums;

/**
 * How much AML risk a customer or transaction carries.
 *
 * <p>Persisted by {@code name()} via {@code @Enumerated(EnumType.STRING)}, never
 * by ordinal — ordinals break the moment someone reorders these constants.
 */
public enum RiskLevel {

    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
