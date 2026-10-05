package com.hemodialyse.backend.infrastructure.web.rest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Régularisation d'une séance oubliée : l'administrateur la déverrouille, puis seulement l'infirmier peut la valider.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class SeanceDeverrouillageIntegrationTest {

    private static final UUID CENTER = UUID.fromString("55555555-5555-5555-5555-555555555551");
    private static final UUID PATIENT = UUID.fromString("55555555-5555-5555-5555-555555555552");
    private static final UUID OUBLIEE = UUID.fromString("55555555-5555-5555-5555-555555555553");
    private static final UUID DU_JOUR = UUID.fromString("55555555-5555-5555-5555-555555555554");

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mockMvc;

    @BeforeEach
    void seed() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanup();
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, "
                        + "type_patient, created_at) VALUES (?, ?, ?, ?, ?, ?, CURRENT_DATE, ?, ?, ?)",
                PATIENT, CENTER, "DEV-001", "Dupont", "Jean", "M", "ASS-DEV-001", "NON_VACANCIER",
                OffsetDateTime.now(ZoneOffset.UTC));
        seance(OUBLIEE, LocalDate.now().minusDays(2));
        seance(DU_JOUR, LocalDate.now());
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM seances WHERE center_id = ?", CENTER);
        jdbc.update("DELETE FROM patients WHERE id = ?", PATIENT);
    }

    private void seance(UUID id, LocalDate date) {
        jdbc.update("INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at) VALUES (?, ?, ?, ?, 'CREE', ?)",
                id, PATIENT, CENTER, date, OffsetDateTime.now(ZoneOffset.UTC));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder valider(UUID id, String role) {
        return post("/api/v1/seances/{id}/valider", id).with(user("u").roles(role))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"centerId\":\"%s\",\"userId\":\"u\"}".formatted(CENTER));
    }

    @Test
    void a_nurse_cannot_validate_a_forgotten_session_until_the_admin_unlocks_it() throws Exception {
        mockMvc.perform(valider(OUBLIEE, "INFIRMIER")).andExpect(status().isUnprocessableEntity());

        mockMvc.perform(post("/api/v1/seances/{id}/deverrouiller-regularisation", OUBLIEE)
                        .param("centerId", CENTER.toString()).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regularisationDeverrouilleeAt").isNotEmpty());

        mockMvc.perform(valider(OUBLIEE, "INFIRMIER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDEE"));
    }

    @Test
    void only_the_admin_can_unlock() throws Exception {
        for (String role : new String[]{"INFIRMIER", "SECRETAIRE", "MEDECIN"}) {
            mockMvc.perform(post("/api/v1/seances/{id}/deverrouiller-regularisation", OUBLIEE)
                            .param("centerId", CENTER.toString()).with(user("u").roles(role)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void todays_session_cannot_be_unlocked_and_a_nurse_validates_it_freely() throws Exception {
        mockMvc.perform(post("/api/v1/seances/{id}/deverrouiller-regularisation", DU_JOUR)
                        .param("centerId", CENTER.toString()).with(user("admin").roles("ADMIN")))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(valider(DU_JOUR, "INFIRMIER")).andExpect(status().isOk());
    }

    @Test
    void the_admin_can_validate_a_forgotten_session_without_unlocking_it() throws Exception {
        mockMvc.perform(valider(OUBLIEE, "ADMIN")).andExpect(status().isOk());
    }

    @Test
    void the_nurse_list_only_shows_unlocked_sessions_while_the_admin_list_shows_them_all() throws Exception {
        String debut = LocalDate.now().minusDays(7).toString();
        String fin = LocalDate.now().minusDays(1).toString();

        mockMvc.perform(get("/api/v1/seances").param("centerId", CENTER.toString()).param("status", "CREE")
                        .param("from", debut).param("to", fin).param("deverrouillee", "true")
                        .with(user("inf").roles("INFIRMIER")))
                .andExpect(jsonPath("$.items", hasSize(0)));

        mockMvc.perform(post("/api/v1/seances/{id}/deverrouiller-regularisation", OUBLIEE)
                .param("centerId", CENTER.toString()).with(user("admin").roles("ADMIN"))).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/seances").param("centerId", CENTER.toString()).param("status", "CREE")
                        .param("from", debut).param("to", fin).param("deverrouillee", "true")
                        .with(user("inf").roles("INFIRMIER")))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].regularisationDeverrouilleeAt").isNotEmpty());
        mockMvc.perform(get("/api/v1/seances").param("centerId", CENTER.toString()).param("status", "CREE")
                        .param("from", debut).param("to", fin).param("deverrouillee", "false")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(jsonPath("$.items", hasSize(0)));
    }
}
