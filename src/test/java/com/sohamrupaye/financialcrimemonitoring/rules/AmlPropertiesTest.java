package com.sohamrupaye.financialcrimemonitoring.rules;

import com.sohamrupaye.financialcrimemonitoring.TestcontainersConfiguration;
import com.sohamrupaye.financialcrimemonitoring.model.enums.RiskLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks that the real {@code application.properties} binds, which the unit tests
 * cannot: they construct {@link AmlProperties} by hand, so a typo in the
 * properties file would go unnoticed until a transaction scored wrongly.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AmlPropertiesTest {

    @Autowired
    private AmlProperties properties;

    @Test
    @DisplayName("thresholds and windows bind from application.properties")
    void bindsRuleSettings() {
        assertThat(properties.largeAmount().threshold()).isEqualByComparingTo("500000");
        assertThat(properties.largeAmount().points()).isEqualTo(25);

        // Written as "10m" and "24h" in the file; relaxed binding parses both.
        assertThat(properties.velocity().window()).isEqualTo(Duration.ofMinutes(10));
        assertThat(properties.structuring().window()).isEqualTo(Duration.ofHours(24));

        assertThat(properties.countryRisk().elevatedRiskCountries())
                .containsExactlyInAnyOrder("XA", "XB", "XC", "QM");

        assertThat(properties.alerting().threshold()).isEqualTo(60);
    }

    @Test
    @DisplayName("the alert threshold sits inside the score scale")
    void alertThresholdIsReachable() {
        // A threshold above maxScore would mean nothing ever alerts, which is a
        // configuration mistake that looks exactly like a quiet system.
        assertThat(properties.alerting().threshold())
                .isLessThanOrEqualTo(properties.scoring().maxScore());
    }

    @Test
    @DisplayName("every risk level has a configured weight")
    void everyRiskLevelIsWeighted() {
        // CustomerRiskRule refuses to start without this, so the assertion is
        // really about keeping that failure out of a deployment.
        assertThat(properties.customerRisk().points())
                .containsOnlyKeys(RiskLevel.values());

        assertThat(Arrays.stream(RiskLevel.values())
                .allMatch(level -> properties.customerRisk().points().get(level) >= 0)).isTrue();
    }

    @Test
    @DisplayName("the structuring floor sits below the large amount threshold")
    void structuringFloorIsBelowThreshold() {
        // If these ever cross, no amount can qualify as structuring and the rule
        // silently stops working - hence the startup guard in StructuringRule.
        assertThat(properties.structuring().nearThresholdFloor())
                .isLessThan(properties.largeAmount().threshold());
    }
}
