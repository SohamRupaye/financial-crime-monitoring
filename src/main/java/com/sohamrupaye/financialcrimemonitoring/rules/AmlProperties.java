package com.sohamrupaye.financialcrimemonitoring.rules;

import com.sohamrupaye.financialcrimemonitoring.model.enums.RiskLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * Every threshold, window and weight the rules use, bound from {@code aml.*}.
 *
 * <p>These were constants until now, which made the readme's claim that rules are
 * configurable untrue. Thresholds are the part of an AML system that changes most
 * often — they get tuned against how many false positives analysts are actually
 * drowning in — and needing a rebuild to change one is not workable.
 *
 * <p>{@code @Validated} matters more than it looks. Without it a typo in
 * {@code application.properties} binds silently: a missing threshold becomes
 * null, a missing weight becomes zero, and the system quietly stops flagging
 * anything. With it, the application refuses to start.
 */
@Validated
@ConfigurationProperties(prefix = "aml")
public record AmlProperties(

        @Valid @NotNull LargeAmount largeAmount,
        @Valid @NotNull Velocity velocity,
        @Valid @NotNull Structuring structuring,
        @Valid @NotNull CustomerRisk customerRisk,
        @Valid @NotNull CountryRisk countryRisk,
        @Valid @NotNull Scoring scoring
) {

    public record LargeAmount(
            @NotNull @Positive BigDecimal threshold,
            @Positive int points
    ) {
    }

    public record Velocity(
            @NotNull Duration window,
            @Positive int maxTransactions,
            @Positive int points
    ) {
    }

    public record Structuring(
            @NotNull Duration window,
            @NotNull @Positive BigDecimal nearThresholdFloor,
            // Two transactions are not a pattern, so the minimum is a real
            // constraint rather than a sanity check.
            @Min(3) int minTransactions,
            @Positive int points
    ) {
    }

    public record CustomerRisk(
            @NotEmpty Map<RiskLevel, @PositiveOrZero Integer> points
    ) {
    }

    public record CountryRisk(
            @NotNull Set<String> elevatedRiskCountries,
            @Positive int points
    ) {
    }

    /**
     * {@code bands} maps each level to the lowest score that reaches it. Held as
     * a floor per level rather than as ranges so the bands cannot overlap or
     * leave a gap — with ranges, 0-29 and 31-59 would silently swallow 30.
     */
    public record Scoring(
            @Positive int maxScore,
            @NotEmpty Map<RiskLevel, @PositiveOrZero Integer> bands
    ) {
    }
}
