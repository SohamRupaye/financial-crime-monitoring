package com.sohamrupaye.financialcrimemonitoring;

import com.sohamrupaye.financialcrimemonitoring.model.Customer;
import com.sohamrupaye.financialcrimemonitoring.model.enums.RiskLevel;
import com.sohamrupaye.financialcrimemonitoring.repository.CustomerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole pipeline over HTTP: customer, account, ingestion, scoring, alert,
 * analyst workflow.
 *
 * <p>Every layer below has its own tests. What only this can catch is the wiring
 * between them — that ingestion really does assess, that assessment really does
 * alert, and that the score a real Postgres round-trip produces is the one the
 * unit tests predicted.
 *
 * <p>{@code @Transactional} rolls each test back, so the two scenarios cannot see
 * each other's data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class EndToEndMonitoringTest {

    /** Truncated to milliseconds because that is the column's resolution. */
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomerRepository customerRepository;

    private JsonNode postJson(String path, String body) throws Exception {
        String response = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response);
    }

    private String createCustomer(String email) throws Exception {
        return postJson("/api/v1/customers", """
                {
                  "firstName": "Asha",
                  "lastName": "Menon",
                  "email": "%s",
                  "dateOfBirth": "1990-05-17",
                  "countryCode": "IN"
                }
                """.formatted(email)).get("customerReference").asString();
    }

    private String openAccount(String customerReference) throws Exception {
        return postJson("/api/v1/customers/" + customerReference + "/accounts", """
                {"accountType": "SAVINGS", "currency": "INR"}
                """).get("accountNumber").asString();
    }

    private String ingest(String accountNumber, String amount, String country, Instant occurredAt)
            throws Exception {

        return postJson("/api/v1/transactions", """
                {
                  "accountNumber": "%s",
                  "transactionType": "TRANSFER",
                  "amount": %s,
                  "currency": "INR",
                  "counterpartyAccountNumber": "ACC-EXTERNAL-8841",
                  "counterpartyCountry": "%s",
                  "occurredAt": "%s"
                }
                """.formatted(accountNumber, amount, country, occurredAt))
                .get("transactionReference").asString();
    }

    /**
     * No endpoint re-rates a customer yet — the readme lists dynamic customer risk
     * as a limitation — so the rating is set directly to exercise the rule.
     */
    private void rate(String customerReference, RiskLevel riskLevel) {
        Customer customer = customerRepository.findByCustomerReference(customerReference)
                .orElseThrow();
        customer.setRiskLevel(riskLevel);
        customerRepository.save(customer);
    }

    @Test
    @DisplayName("split transfers by an elevated-risk customer raise an alert an analyst can work")
    void structuringRaisesAnAlert() throws Exception {
        String customerReference = createCustomer("structuring@example.com");
        rate(customerReference, RiskLevel.HIGH);
        String accountNumber = openAccount(customerReference);

        // Three domestic transfers, each under the 500,000 line. The third scores
        // 50 - structuring plus customer risk - which is below the alert
        // threshold, so nothing has fired yet.
        ingest(accountNumber, "490000.00", "IN", NOW.minus(Duration.ofMinutes(30)));
        ingest(accountNumber, "490000.00", "IN", NOW.minus(Duration.ofMinutes(20)));
        ingest(accountNumber, "480000.00", "IN", NOW.minus(Duration.ofMinutes(10)));

        // The fourth is to a watched jurisdiction, which adds the 20 points that
        // take it over the line.
        String transactionReference =
                ingest(accountNumber, "495000.00", "XA", NOW.minus(Duration.ofSeconds(5)));

        // 30 structuring + 20 customer risk + 20 country risk = 70.
        mockMvc.perform(get("/api/v1/transactions/{ref}/assessment", transactionReference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(70))
                .andExpect(jsonPath("$.level").value("HIGH"))
                .andExpect(jsonPath("$.reasons.length()").value(3))
                .andExpect(jsonPath("$.reasons[0]").value(
                        "4 transactions totalling 1955000.00 INR in 24 hours, "
                                + "each individually below the 500000 threshold"))
                .andExpect(jsonPath("$.reasons[1]").value("Customer risk rating is HIGH"))
                .andExpect(jsonPath("$.reasons[2]").value(
                        "Counterparty country XA requires additional scrutiny"))
                // All five rules reported, including the two that stayed quiet.
                .andExpect(jsonPath("$.rules.length()").value(5));

        // Exactly one alert: the three earlier transactions did not clear the
        // threshold, so an analyst sees one item rather than four.
        String alerts = mockMvc.perform(get("/api/v1/alerts").param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].transactionReference")
                        .value(transactionReference))
                .andExpect(jsonPath("$.content[0].score").value(70))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String alertReference = objectMapper.readTree(alerts)
                .get("content").get(0).get("alertReference").asString();

        assertThat(alertReference).startsWith("ALRT-");

        // The alert carries the whole picture, so an analyst does not have to go
        // fetch the transaction and the customer separately.
        mockMvc.perform(get("/api/v1/alerts/{ref}", alertReference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.customerReference").value(customerReference))
                .andExpect(jsonPath("$.customerRiskLevel").value("HIGH"))
                .andExpect(jsonPath("$.accountNumber").value(accountNumber))
                .andExpect(jsonPath("$.amount").value(495000.0000))
                .andExpect(jsonPath("$.counterpartyCountry").value("XA"));

        // Re-running the rules against current configuration. Same five codes as
        // the first pass, which is the case that has to update rows rather than
        // insert new ones.
        mockMvc.perform(post("/api/v1/transactions/{ref}/evaluate", transactionReference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(70))
                .andExpect(jsonPath("$.rules.length()").value(5));

        // And it does not stack a second alert onto the same finding.
        mockMvc.perform(get("/api/v1/alerts").param("status", "OPEN"))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(patch("/api/v1/alerts/{ref}/status", alertReference)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"ACKNOWLEDGED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));

        // Acknowledged is not investigated, so it cannot be resolved from here.
        mockMvc.perform(patch("/api/v1/alerts/{ref}/status", alertReference)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"RESOLVED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Status transition not allowed"));

        // It is out of the OPEN queue now.
        mockMvc.perform(get("/api/v1/alerts").param("status", "OPEN"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("ordinary activity scores zero and raises nothing")
    void ordinaryActivityRaisesNoAlert() throws Exception {
        String customerReference = createCustomer("ordinary@example.com");
        String accountNumber = openAccount(customerReference);

        String transactionReference =
                ingest(accountNumber, "4500.00", "IN", NOW.minus(Duration.ofMinutes(5)));

        // The other half of the job. A monitoring system that alerts on everything
        // is as useless as one that alerts on nothing, so the quiet path is worth
        // asserting too.
        mockMvc.perform(get("/api/v1/transactions/{ref}/assessment", transactionReference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(0))
                .andExpect(jsonPath("$.level").value("LOW"))
                .andExpect(jsonPath("$.reasons").isEmpty())
                .andExpect(jsonPath("$.rules.length()").value(5));

        mockMvc.perform(get("/api/v1/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
