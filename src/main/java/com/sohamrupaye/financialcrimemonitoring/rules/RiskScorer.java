package com.sohamrupaye.financialcrimemonitoring.rules;

import com.sohamrupaye.financialcrimemonitoring.model.enums.RiskLevel;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Turns a set of rule results into a score and a level.
 *
 * <p>Separate from {@link AmlRulesEngine} on purpose: the engine gathers evidence,
 * this weighs it. Splitting them means the weighting can change — different
 * bands, a cap, eventually a model — without touching a single rule.
 */
@Component
public class RiskScorer {

    private final int maxScore;

    /** Levels paired with their floor, highest floor first. */
    private final List<Map.Entry<RiskLevel, Integer>> bandsDescending;

    public RiskScorer(AmlProperties properties) {
        AmlProperties.Scoring config = properties.scoring();

        List<RiskLevel> missing = Arrays.stream(RiskLevel.values())
                .filter(level -> !config.bands().containsKey(level))
                .toList();

        if (!missing.isEmpty()) {
            throw new IllegalStateException("aml.scoring.bands is missing an entry for " + missing);
        }

        // The lowest level has to start at zero, or a score below its floor would
        // match no band at all and there would be nothing sensible to return.
        if (config.bands().get(RiskLevel.LOW) != 0) {
            throw new IllegalStateException("aml.scoring.bands.low must be 0");
        }

        this.maxScore = config.maxScore();
        this.bandsDescending = config.bands().entrySet().stream()
                .sorted(Comparator.<Map.Entry<RiskLevel, Integer>>comparingInt(Map.Entry::getValue)
                        .reversed())
                .toList();
    }

    public RiskScore score(List<RuleResult> results) {
        int total = results.stream()
                .filter(RuleResult::triggered)
                .mapToInt(RuleResult::points)
                .sum();

        int capped = Math.min(total, maxScore);
        return new RiskScore(capped, levelFor(capped));
    }

    /**
     * First band whose floor the score reaches, searching from the top. LOW has a
     * floor of zero, so this always finds one.
     */
    private RiskLevel levelFor(int score) {
        return bandsDescending.stream()
                .filter(band -> score >= band.getValue())
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no band matched score " + score));
    }
}
