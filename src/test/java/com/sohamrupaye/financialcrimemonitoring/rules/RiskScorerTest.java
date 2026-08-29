package com.sohamrupaye.financialcrimemonitoring.rules;

import com.sohamrupaye.financialcrimemonitoring.model.enums.RiskLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.properties;
import static com.sohamrupaye.financialcrimemonitoring.rules.RuleFixtures.withScoring;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RiskScorerTest {

    private final RiskScorer scorer = new RiskScorer(properties());

    private static RuleResult fired(RuleCode code, int points) {
        return RuleResult.triggered(code, points, code + " fired");
    }

    /**
     * Results adding up to {@code points}.
     *
     * <p>Zero needs an untriggered result rather than a triggered one worth
     * nothing, because {@link RuleResult} rejects that outright — a rule that
     * fires has to have both a weight and something to say.
     */
    private static List<RuleResult> resultsWorth(int points) {
        return points == 0
                ? List.of(RuleResult.notTriggered(RuleCode.LARGE_AMOUNT))
                : List.of(fired(RuleCode.LARGE_AMOUNT, points));
    }

    @Test
    @DisplayName("only triggered rules contribute points")
    void sumsTriggeredPointsOnly() {
        RiskScore score = scorer.score(List.of(
                fired(RuleCode.LARGE_AMOUNT, 25),
                RuleResult.notTriggered(RuleCode.VELOCITY),
                fired(RuleCode.CUSTOMER_RISK, 20)));

        assertThat(score.score()).isEqualTo(45);
        assertThat(score.level()).isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    @DisplayName("a clean transaction scores zero and lands in LOW")
    void scoresZeroWhenNothingTriggers() {
        RiskScore score = scorer.score(List.of(
                RuleResult.notTriggered(RuleCode.LARGE_AMOUNT),
                RuleResult.notTriggered(RuleCode.VELOCITY)));

        assertThat(score.score()).isZero();
        assertThat(score.level()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    @DisplayName("every rule firing at once is capped at the maximum")
    void capsAtMaximum() {
        // 25 + 20 + 30 + 30 + 20 = 125. Left uncapped the score would run off
        // the end of its own scale, and the CHECK constraint on the column would
        // reject the row.
        RiskScore score = scorer.score(List.of(
                fired(RuleCode.LARGE_AMOUNT, 25),
                fired(RuleCode.VELOCITY, 20),
                fired(RuleCode.STRUCTURING, 30),
                fired(RuleCode.CUSTOMER_RISK, 30),
                fired(RuleCode.COUNTRY_RISK, 20)));

        assertThat(score.score()).isEqualTo(100);
        assertThat(score.level()).isEqualTo(RiskLevel.CRITICAL);
    }

    @Nested
    @DisplayName("band boundaries")
    class BandBoundaries {

        @ParameterizedTest
        @CsvSource({
                "0,LOW", "29,LOW",
                "30,MEDIUM", "59,MEDIUM",
                "60,HIGH", "79,HIGH",
                "80,CRITICAL", "100,CRITICAL"
        })
        @DisplayName("each boundary falls on the side the configuration says")
        void boundariesAreInclusiveOfTheirFloor(int points, RiskLevel expected) {
            // Off-by-one here is invisible in normal use and changes whether an
            // alert fires, so every edge is pinned rather than sampled.
            RiskScore score = scorer.score(resultsWorth(points));

            assertThat(score.score()).isEqualTo(points);
            assertThat(score.level()).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("configuration guards")
    class ConfigurationGuards {

        @Test
        @DisplayName("a missing band fails at startup")
        void rejectsMissingBand() {
            assertThatThrownBy(() -> new RiskScorer(withScoring(
                    new AmlProperties.Scoring(100, Map.of(
                            RiskLevel.LOW, 0, RiskLevel.MEDIUM, 30, RiskLevel.HIGH, 60)))))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CRITICAL");
        }

        @Test
        @DisplayName("the lowest band has to start at zero")
        void rejectsNonZeroLowestBand() {
            // Otherwise a score under that floor matches no band and there is
            // nothing meaningful to return.
            assertThatThrownBy(() -> new RiskScorer(withScoring(
                    new AmlProperties.Scoring(100, Map.of(
                            RiskLevel.LOW, 10, RiskLevel.MEDIUM, 30,
                            RiskLevel.HIGH, 60, RiskLevel.CRITICAL, 80)))))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("must be 0");
        }
    }
}
