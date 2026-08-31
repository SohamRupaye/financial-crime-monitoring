package com.sohamrupaye.financialcrimemonitoring.dto;

import com.sohamrupaye.financialcrimemonitoring.model.enums.AlertStatus;
import jakarta.validation.constraints.NotNull;

/**
 * The one field a client may change on an alert.
 *
 * <p>Everything else about an alert is derived from the assessment that raised
 * it, so there is nothing else here to expose.
 */
public record UpdateAlertStatusRequest(

        @NotNull(message = "status is required")
        AlertStatus status
) {
}
