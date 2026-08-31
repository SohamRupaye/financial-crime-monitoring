package com.sohamrupaye.financialcrimemonitoring.controller;

import com.sohamrupaye.financialcrimemonitoring.dto.AlertResponse;
import com.sohamrupaye.financialcrimemonitoring.dto.AlertSummaryResponse;
import com.sohamrupaye.financialcrimemonitoring.dto.RuleResultResponse;
import com.sohamrupaye.financialcrimemonitoring.dto.UpdateAlertStatusRequest;
import com.sohamrupaye.financialcrimemonitoring.exception.IllegalStatusTransitionException;
import com.sohamrupaye.financialcrimemonitoring.exception.ResourceNotFoundException;
import com.sohamrupaye.financialcrimemonitoring.model.enums.AlertStatus;
import com.sohamrupaye.financialcrimemonitoring.model.enums.RiskLevel;
import com.sohamrupaye.financialcrimemonitoring.model.enums.TransactionType;
import com.sohamrupaye.financialcrimemonitoring.rules.RuleCode;
import com.sohamrupaye.financialcrimemonitoring.service.AlertService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AlertController.class)
@AutoConfigureMockMvc(addFilters = false)
class AlertControllerTest {

    private static final String ALERT_REFERENCE = "ALRT-7C1D40A9";
    private static final Instant RAISED_AT = Instant.parse("2026-09-01T10:00:05Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AlertService alertService;

    private static AlertResponse detail(AlertStatus status) {
        return new AlertResponse(
                ALERT_REFERENCE, status, RAISED_AT, RAISED_AT,
                85, RiskLevel.CRITICAL, RAISED_AT,
                List.of("Amount 485000.00 INR exceeded the 500000 threshold"),
                List.of(new RuleResultResponse(RuleCode.LARGE_AMOUNT, true, 25,
                                "Amount 485000.00 INR exceeded the 500000 threshold"),
                        new RuleResultResponse(RuleCode.VELOCITY, false, 0, null)),
                "TXN-93842A1C", TransactionType.TRANSFER, new BigDecimal("485000.0000"),
                "INR", "ACC-EXTERNAL-8841", "XA", Instant.parse("2026-09-01T10:00:00Z"),
                "ACC-9B41C7E20D5A", "CUST-3F2A9C41", RiskLevel.HIGH);
    }

    private static AlertSummaryResponse summary() {
        return new AlertSummaryResponse(ALERT_REFERENCE, AlertStatus.OPEN, RAISED_AT,
                85, RiskLevel.CRITICAL, "TXN-93842A1C", new BigDecimal("485000.0000"),
                "INR", "ACC-9B41C7E20D5A", "CUST-3F2A9C41");
    }

    @Test
    @DisplayName("GET lists alerts without the per-rule detail")
    void listReturnsSummaries() throws Exception {
        when(alertService.search(isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(summary()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].alertReference").value(ALERT_REFERENCE))
                .andExpect(jsonPath("$.content[0].score").value(85))
                // Kept out of the list on purpose: fetching them alongside a page
                // would make Hibernate paginate in memory.
                .andExpect(jsonPath("$.content[0].rules").doesNotExist());
    }

    @Test
    @DisplayName("GET passes a status filter through")
    void listFiltersByStatus() throws Exception {
        when(alertService.search(eq(AlertStatus.OPEN), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(summary()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/alerts").param("status", "OPEN"))
                .andExpect(status().isOk());

        verify(alertService).search(eq(AlertStatus.OPEN), any(Pageable.class));
    }

    @Test
    @DisplayName("GET with an unknown status returns 400, not 500")
    void listRejectsUnknownStatus() throws Exception {
        mockMvc.perform(get("/api/v1/alerts").param("status", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid parameter"));
    }

    @Test
    @DisplayName("GET by reference returns the full alert with its reasons")
    void getReturnsDetail() throws Exception {
        when(alertService.findByReference(ALERT_REFERENCE))
                .thenReturn(detail(AlertStatus.OPEN));

        mockMvc.perform(get("/api/v1/alerts/{ref}", ALERT_REFERENCE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.customerReference").value("CUST-3F2A9C41"))
                .andExpect(jsonPath("$.reasons.length()").value(1))
                .andExpect(jsonPath("$.rules.length()").value(2));
    }

    @Test
    @DisplayName("GET for an unknown alert returns 404")
    void getUnknownReturnsNotFound() throws Exception {
        when(alertService.findByReference("ALRT-NOPE"))
                .thenThrow(ResourceNotFoundException.of("Alert", "ALRT-NOPE"));

        mockMvc.perform(get("/api/v1/alerts/{ref}", "ALRT-NOPE"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH moves the alert on")
    void patchUpdatesStatus() throws Exception {
        when(alertService.updateStatus(ALERT_REFERENCE, AlertStatus.ACKNOWLEDGED))
                .thenReturn(detail(AlertStatus.ACKNOWLEDGED));

        mockMvc.perform(patch("/api/v1/alerts/{ref}/status", ALERT_REFERENCE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateAlertStatusRequest(AlertStatus.ACKNOWLEDGED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));
    }

    @Test
    @DisplayName("an illegal transition is 409, not 400 - the request was fine")
    void patchIllegalTransitionReturnsConflict() throws Exception {
        when(alertService.updateStatus(ALERT_REFERENCE, AlertStatus.RESOLVED))
                .thenThrow(new IllegalStatusTransitionException(
                        "Alert ALRT-7C1D40A9 cannot move from OPEN to RESOLVED. "
                                + "Allowed: [ACKNOWLEDGED, FALSE_POSITIVE]"));

        mockMvc.perform(patch("/api/v1/alerts/{ref}/status", ALERT_REFERENCE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateAlertStatusRequest(AlertStatus.RESOLVED))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Status transition not allowed"))
                .andExpect(jsonPath("$.detail").value(
                        "Alert ALRT-7C1D40A9 cannot move from OPEN to RESOLVED. "
                                + "Allowed: [ACKNOWLEDGED, FALSE_POSITIVE]"));
    }

    @Test
    @DisplayName("PATCH without a status is rejected")
    void patchRejectsMissingStatus() throws Exception {
        mockMvc.perform(patch("/api/v1/alerts/{ref}/status", ALERT_REFERENCE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.status").value("status is required"));
    }

    @Test
    @DisplayName("PATCH with an unknown status lists the valid ones")
    void patchRejectsUnknownStatus() throws Exception {
        mockMvc.perform(patch("/api/v1/alerts/{ref}/status", ALERT_REFERENCE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "'CLOSED' is not a valid AlertStatus. Allowed values: "
                                + "[OPEN, ACKNOWLEDGED, INVESTIGATING, RESOLVED, FALSE_POSITIVE]"));
    }
}
