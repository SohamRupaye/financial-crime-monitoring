package com.sohamrupaye.financialcrimemonitoring.rules;

import com.sohamrupaye.financialcrimemonitoring.model.enums.RiskLevel;

/** The outcome of scoring one set of rule results. */
public record RiskScore(int score, RiskLevel level) {
}
