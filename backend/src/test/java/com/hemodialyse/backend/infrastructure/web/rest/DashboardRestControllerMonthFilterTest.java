package com.hemodialyse.backend.infrastructure.web.rest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class DashboardRestControllerMonthFilterTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mockMvc;
    private UUID centerId;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        centerId = UUID.randomUUID();
        cleanupTestData();
        seedTestData();
    }

    private void cleanupTestData() {
        jdbc.update("DELETE FROM prise_en_charge WHERE center_id = ?", centerId);
        jdbc.update("DELETE FROM attestation_droit WHERE center_id = ?", centerId);
        jdbc.update("DELETE FROM patients WHERE center_id = ?", centerId);
    }

    private void seedTestData() {
        // Insert test patient
        jdbc.update(
                "INSERT INTO patients (id, center_id, nom, prenom, created_at) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), centerId, "Dupont", "Jean", LocalDate.now()
        );

        LocalDate today = LocalDate.now();
        LocalDate lastMonth = today.minusMonths(1);
        LocalDate twoMonthsAgo = today.minusMonths(2);

        // Insert PEC for current month
        jdbc.update(
                "INSERT INTO prise_en_charge (id, center_id, statut, created_at, date_fin_demande) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), centerId, "CREE", today, today.plusDays(30)
        );

        // Insert PEC for last month
        jdbc.update(
                "INSERT INTO prise_en_charge (id, center_id, statut, created_at, date_fin_demande) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), centerId, "VALIDEE", lastMonth, lastMonth.plusDays(30)
        );

        // Insert PEC for two months ago
        jdbc.update(
                "INSERT INTO prise_en_charge (id, center_id, statut, created_at, date_fin_demande) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), centerId, "VALIDEE", twoMonthsAgo, twoMonthsAgo.plusDays(5)
        );

        // Insert attestation for current month
        jdbc.update(
                "INSERT INTO attestation_droit (id, center_id, created_at, date_fin) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), centerId, today, today.plusMonths(3)
        );

        // Insert attestation for last month
        jdbc.update(
                "INSERT INTO attestation_droit (id, center_id, created_at, date_fin) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), centerId, lastMonth, lastMonth.plusMonths(3)
        );
    }

    @Test
    void getDashboardStats_without_month_filter_should_return_all_stats() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/stats")
                        .param("centerId", centerId.toString())
                        .param("expirationDays", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientCount", is(1)))
                .andExpect(jsonPath("$.pecCree", is(1)))
                .andExpect(jsonPath("$.pecValidee", is(2)))
                .andExpect(jsonPath("$.attestationTotal", is(2)));
    }

    @Test
    void getDashboardStats_with_current_month_filter_should_return_only_current_month_data() throws Exception {
        String currentMonth = YearMonth.now().toString();

        mockMvc.perform(get("/api/v1/dashboard/stats")
                        .param("centerId", centerId.toString())
                        .param("expirationDays", "30")
                        .param("month", currentMonth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientCount", is(1)))
                .andExpect(jsonPath("$.pecCree", is(1)))
                .andExpect(jsonPath("$.pecValidee", is(0)))
                .andExpect(jsonPath("$.attestationTotal", is(1)))
                .andExpect(jsonPath("$.month", is(currentMonth)));
    }

    @Test
    void getDashboardStats_with_last_month_filter_should_return_only_last_month_data() throws Exception {
        String lastMonth = YearMonth.now().minusMonths(1).toString();

        mockMvc.perform(get("/api/v1/dashboard/stats")
                        .param("centerId", centerId.toString())
                        .param("expirationDays", "30")
                        .param("month", lastMonth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientCount", is(0)))
                .andExpect(jsonPath("$.pecCree", is(0)))
                .andExpect(jsonPath("$.pecValidee", is(1)))
                .andExpect(jsonPath("$.attestationTotal", is(1)))
                .andExpect(jsonPath("$.month", is(lastMonth)));
    }

    @Test
    void getDashboardStats_with_invalid_month_format_should_handle_gracefully() throws Exception {
        // Invalid month format should be ignored (treated as no filter)
        mockMvc.perform(get("/api/v1/dashboard/stats")
                        .param("centerId", centerId.toString())
                        .param("expirationDays", "30")
                        .param("month", "invalid-month"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientCount", is(1)));
    }

    @Test
    void searchStats_with_month_filter_should_apply_filter() throws Exception {
        String currentMonth = YearMonth.now().toString();

        String requestBody = String.format("""
                {
                  "centerId": "%s",
                  "expirationDays": 30,
                  "month": "%s"
                }
                """, centerId, currentMonth);

        mockMvc.perform(post("/api/v1/dashboard/stats/search")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pecCree", is(1)))
                .andExpect(jsonPath("$.month", is(currentMonth)));
    }

    @Test
    void getDashboardStats_month_filter_should_isolate_by_center() throws Exception {
        UUID otherCenterId = UUID.randomUUID();
        LocalDate today = LocalDate.now();

        // Add PEC to different center with same month
        jdbc.update(
                "INSERT INTO prise_en_charge (id, center_id, statut, created_at, date_fin_demande) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), otherCenterId, "CREE", today, today.plusDays(30)
        );

        String currentMonth = YearMonth.now().toString();

        MvcResult result = mockMvc.perform(get("/api/v1/dashboard/stats")
                        .param("centerId", centerId.toString())
                        .param("expirationDays", "30")
                        .param("month", currentMonth))
                .andExpect(status().isOk())
                .andReturn();

        // Should only count PEC for current center
        mockMvc.perform(get("/api/v1/dashboard/stats")
                        .param("centerId", centerId.toString())
                        .param("expirationDays", "30")
                        .param("month", currentMonth))
                .andExpect(jsonPath("$.pecCree", is(1)));
    }
}

