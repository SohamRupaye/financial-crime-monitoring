package com.sohamrupaye.financialcrimemonitoring.controller;

import com.sohamrupaye.financialcrimemonitoring.dto.AlertResponse;
import com.sohamrupaye.financialcrimemonitoring.dto.AlertSummaryResponse;
import com.sohamrupaye.financialcrimemonitoring.dto.UpdateAlertStatusRequest;
import com.sohamrupaye.financialcrimemonitoring.model.enums.AlertStatus;
import com.sohamrupaye.financialcrimemonitoring.service.AlertService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The analyst-facing end of the system. Alerts are never created here — they are
 * raised by the rules engine — so there is no POST.
 */
@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    /**
     * {@code GET /api/v1/alerts?status=OPEN&size=20}
     *
     * <p>Newest first by default: an alert queue is worked from the top, and a
     * transaction that fired an hour ago is more actionable than one from a month
     * ago.
     */
    @GetMapping
    public Page<AlertSummaryResponse> list(
            @RequestParam(required = false) AlertStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        return alertService.search(status, pageable);
    }

    /** {@code GET /api/v1/alerts/ALRT-7C1D40A9} */
    @GetMapping("/{alertReference}")
    public AlertResponse get(@PathVariable String alertReference) {
        return alertService.findByReference(alertReference);
    }

    /**
     * {@code PATCH /api/v1/alerts/ALRT-7C1D40A9/status}
     *
     * <p>PATCH rather than PUT because status is the only mutable field on an
     * alert; a PUT would imply the caller is replacing the whole thing, which it
     * cannot. An illegal move is a 409.
     */
    @PatchMapping("/{alertReference}/status")
    public AlertResponse updateStatus(
            @PathVariable String alertReference,
            @Valid @RequestBody UpdateAlertStatusRequest request) {

        return alertService.updateStatus(alertReference, request.status());
    }
}
