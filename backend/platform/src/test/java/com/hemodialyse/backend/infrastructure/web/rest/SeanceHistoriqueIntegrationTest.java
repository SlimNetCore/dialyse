package com.hemodialyse.backend.infrastructure.web.rest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Historique des séances : filtre, tri et pagination faits en base, isolés par centre.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class SeanceHistoriqueIntegrationTest {

    private static final UUID CENTER_A = UUID.fromString("99999999-9999-9999-9999-999999999991");
    private static final UUID CENTER_B = UUID.fromString("99999999-9999-9999-9999-999999999992");
    private static final UUID DUPONT = UUID.fromString("99999999-9999-9999-9999-999999999993");
    private static final UUID BENALI = UUID.fromString("99999999-9999-9999-9999-999999999994");
    private static final UUID AUTRE_CENTRE = UUID.fromString("99999999-9999-9999-9999-999999999995");

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mockMvc;

    @BeforeEach
    void seed() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanup();
        patient(DUPONT, CENTER_A, "SHI-001", "Dupont", "Jean");
        patient(BENALI, CENTER_A, "SHI-002", "Benali", "Amine");
        patient(AUTRE_CENTRE, CENTER_B, "SHI-003", "Dupont", "Paul");
        seance(DUPONT, CENTER_A, "2026-10-01", "VALIDEE");
        seance(DUPONT, CENTER_A, "2026-10-03", "CREE");
        seance(BENALI, CENTER_A, "2026-10-02", "FACTUREE");
        seance(BENALI, CENTER_A, "2026-09-15", "VALIDEE");
        seance(AUTRE_CENTRE, CENTER_B, "2026-10-02", "VALIDEE");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM seances WHERE center_id IN (?, ?)", CENTER_A, CENTER_B);
        jdbc.update("DELETE FROM patients WHERE center_id IN (?, ?)", CENTER_A, CENTER_B);
    }

    private void patient(UUID id, UUID center, String code, String nom, String prenom) {
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, "
                        + "type_patient, created_at) VALUES (?, ?, ?, ?, ?, ?, CURRENT_DATE, ?, ?, ?)",
                id, center, code, nom, prenom, "M", "ASS-" + code, "NON_VACANCIER", OffsetDateTime.now(ZoneOffset.UTC));
    }

    private void seance(UUID patient, UUID center, String date, String statut) {
        jdbc.update("INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), patient, center, LocalDate.parse(date), statut, OffsetDateTime.now(ZoneOffset.UTC));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder list(UUID center) {
        return get("/api/v1/seances").param("centerId", center.toString()).with(user("inf").roles("INFIRMIER"));
    }

    @Test
    void by_default_the_history_is_newest_first_and_never_shows_another_center() throws Exception {
        mockMvc.perform(list(CENTER_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.items", hasSize(4)))
                .andExpect(jsonPath("$.items[0].dateSeance").value("2026-10-03"))
                .andExpect(jsonPath("$.items[0].patientNom").value("Dupont"))
                .andExpect(jsonPath("$.items[0].patientCode").value("SHI-001"));
        mockMvc.perform(list(CENTER_B))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].patientPrenom").value("Paul"));
    }

    @Test
    void the_text_search_matches_name_first_name_or_code_with_every_word() throws Exception {
        mockMvc.perform(list(CENTER_A).param("q", "dup"))
                .andExpect(jsonPath("$.total").value(2));
        mockMvc.perform(list(CENTER_A).param("q", "amine benali"))
                .andExpect(jsonPath("$.total").value(2));
        mockMvc.perform(list(CENTER_A).param("q", "shi-002"))
                .andExpect(jsonPath("$.total").value(2));
        mockMvc.perform(list(CENTER_A).param("q", "dupont amine"))
                .andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(list(CENTER_A).param("q", "%"))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void the_period_and_the_status_filters_combine() throws Exception {
        mockMvc.perform(list(CENTER_A).param("from", "2026-10-01").param("to", "2026-10-02"))
                .andExpect(jsonPath("$.total").value(2));
        mockMvc.perform(list(CENTER_A).param("status", "VALIDEE"))
                .andExpect(jsonPath("$.total").value(2));
        mockMvc.perform(list(CENTER_A).param("status", "VALIDEE,CREE").param("from", "2026-10-01"))
                .andExpect(jsonPath("$.total").value(2));
        mockMvc.perform(list(CENTER_A).param("status", "NOPE")).andExpect(status().isBadRequest());
    }

    @Test
    void the_sort_is_done_in_the_database_on_whitelisted_columns() throws Exception {
        mockMvc.perform(list(CENTER_A).param("sortBy", "patient").param("sortDir", "asc"))
                .andExpect(jsonPath("$.items[0].patientNom").value("Benali"))
                .andExpect(jsonPath("$.items[3].patientNom").value("Dupont"));
        mockMvc.perform(list(CENTER_A).param("sortBy", "dateSeance").param("sortDir", "asc"))
                .andExpect(jsonPath("$.items[0].dateSeance").value("2026-09-15"));
        mockMvc.perform(list(CENTER_A).param("sortBy", "status").param("sortDir", "asc"))
                .andExpect(jsonPath("$.items[0].status").value("CREE"));
        mockMvc.perform(list(CENTER_A).param("sortBy", "1; drop table seances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].dateSeance").value("2026-10-03"));
    }

    @Test
    void pagination_slices_the_filtered_set_without_overlap_and_reports_the_filtered_total() throws Exception {
        mockMvc.perform(list(CENTER_A).param("size", "3").param("page", "0"))
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.items[2].dateSeance").value("2026-10-01"));
        mockMvc.perform(list(CENTER_A).param("size", "3").param("page", "1"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].dateSeance").value("2026-09-15"));
        mockMvc.perform(list(CENTER_A).param("q", "dupont").param("size", "1").param("page", "1"))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].dateSeance").value("2026-10-01"));
    }
}
