package com.sohamrupaye.financialcrimemonitoring.model;

import com.sohamrupaye.financialcrimemonitoring.exception.IllegalStatusTransitionException;
import com.sohamrupaye.financialcrimemonitoring.model.enums.AccountStatus;
import com.sohamrupaye.financialcrimemonitoring.model.enums.AccountType;
import com.sohamrupaye.financialcrimemonitoring.model.enums.AlertStatus;
import com.sohamrupaye.financialcrimemonitoring.model.enums.RiskLevel;
import com.sohamrupaye.financialcrimemonitoring.model.enums.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The alert workflow, tested on the entity that owns it. No Spring and no
 * database — a state machine is exactly the kind of thing that should not need
 * either.
 */
class AlertTest {

    private static Alert alertIn(AlertStatus status) {
        Alert alert = new Alert("ALRT-7C1D40A9", assessment());

        // Walked there through legal moves rather than reflected into place, so
        // the fixture cannot set up a state the workflow forbids.
        switch (status) {
            case OPEN -> { }
            case ACKNOWLEDGED -> alert.transitionTo(AlertStatus.ACKNOWLEDGED);
            case INVESTIGATING -> {
                alert.transitionTo(AlertStatus.ACKNOWLEDGED);
                alert.transitionTo(AlertStatus.INVESTIGATING);
            }
            case RESOLVED -> {
                alert.transitionTo(AlertStatus.ACKNOWLEDGED);
                alert.transitionTo(AlertStatus.INVESTIGATING);
                alert.transitionTo(AlertStatus.RESOLVED);
            }
            case FALSE_POSITIVE -> alert.transitionTo(AlertStatus.FALSE_POSITIVE);
        }

        return alert;
    }

    private static RiskAssessment assessment() {
        Customer customer = new Customer("CUST-3F2A9C41", "Asha", "Menon",
                "asha.menon@example.com", LocalDate.of(1990, 5, 17), "IN", RiskLevel.HIGH);

        Account account = new Account("ACC-9B41C7E20D5A", customer, "INR", BigDecimal.ZERO,
                LocalDate.of(2026, 1, 10), AccountType.SAVINGS, AccountStatus.ACTIVE);

        Transaction transaction = new Transaction("TXN-93842A1C", account,
                TransactionType.TRANSFER, new BigDecimal("485000.00"), "INR",
                "ACC-EXTERNAL-8841", "XA", Instant.parse("2026-09-01T10:00:00Z"));

        return new RiskAssessment(transaction, 85, RiskLevel.CRITICAL,
                Instant.parse("2026-09-01T10:00:05Z"));
    }

    @Test
    @DisplayName("a new alert starts OPEN")
    void startsOpen() {
        assertThat(new Alert("ALRT-7C1D40A9", assessment()).getStatus())
                .isEqualTo(AlertStatus.OPEN);
    }

    @Nested
    @DisplayName("legal moves")
    class LegalMoves {

        @ParameterizedTest
        @CsvSource({
                "OPEN,ACKNOWLEDGED",
                "OPEN,FALSE_POSITIVE",
                "ACKNOWLEDGED,INVESTIGATING",
                "ACKNOWLEDGED,FALSE_POSITIVE",
                "INVESTIGATING,RESOLVED",
                "INVESTIGATING,FALSE_POSITIVE"
        })
        @DisplayName("each allowed transition is accepted")
        void allowsTransition(AlertStatus from, AlertStatus to) {
            Alert alert = alertIn(from);

            alert.transitionTo(to);

            assertThat(alert.getStatus()).isEqualTo(to);
        }

        @Test
        @DisplayName("FALSE_POSITIVE is reachable straight from OPEN")
        void falsePositiveNeedsNoCeremony() {
            // An analyst who can see at a glance that the rules were wrong should
            // not have to walk an alert through the whole chain to say so.
            Alert alert = alertIn(AlertStatus.OPEN);

            alert.transitionTo(AlertStatus.FALSE_POSITIVE);

            assertThat(alert.getStatus()).isEqualTo(AlertStatus.FALSE_POSITIVE);
        }
    }

    @Nested
    @DisplayName("refused moves")
    class RefusedMoves {

        @Test
        @DisplayName("an alert cannot be resolved without being investigated")
        void cannotSkipToResolved() {
            // Resolving means somebody looked. Allowing OPEN to RESOLVED would let
            // a queue be cleared without anyone having done so.
            assertThatThrownBy(() -> alertIn(AlertStatus.OPEN)
                    .transitionTo(AlertStatus.RESOLVED))
                    .isInstanceOf(IllegalStatusTransitionException.class)
                    .hasMessageContaining("cannot move from OPEN to RESOLVED");
        }

        @Test
        @DisplayName("an alert cannot go back to OPEN")
        void cannotReopen() {
            assertThatThrownBy(() -> alertIn(AlertStatus.INVESTIGATING)
                    .transitionTo(AlertStatus.OPEN))
                    .isInstanceOf(IllegalStatusTransitionException.class);
        }

        @Test
        @DisplayName("an alert cannot skip acknowledgement")
        void cannotSkipAcknowledgement() {
            assertThatThrownBy(() -> alertIn(AlertStatus.OPEN)
                    .transitionTo(AlertStatus.INVESTIGATING))
                    .isInstanceOf(IllegalStatusTransitionException.class);
        }

        @ParameterizedTest
        @EnumSource(AlertStatus.class)
        @DisplayName("nothing moves out of a terminal state")
        void terminalStatesAreFinal(AlertStatus target) {
            for (AlertStatus terminal : new AlertStatus[]{
                    AlertStatus.RESOLVED, AlertStatus.FALSE_POSITIVE}) {

                assertThatThrownBy(() -> alertIn(terminal).transitionTo(target))
                        .isInstanceOf(IllegalStatusTransitionException.class)
                        .hasMessageContaining("is final");
            }
        }

        @ParameterizedTest
        @EnumSource(AlertStatus.class)
        @DisplayName("moving to the status it already has is refused")
        void refusesSelfTransition(AlertStatus status) {
            // A repeated PATCH is more likely a client that lost track of the
            // state than a deliberate no-op, and saying so is more useful than
            // silently agreeing.
            assertThatThrownBy(() -> alertIn(status).transitionTo(status))
                    .isInstanceOf(IllegalStatusTransitionException.class);
        }
    }
}
