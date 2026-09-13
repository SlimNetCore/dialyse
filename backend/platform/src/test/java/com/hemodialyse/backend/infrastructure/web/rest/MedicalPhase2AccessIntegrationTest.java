package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
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
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Matrice d'accès et isolation multi-centres (AGENTS.md §2 et §7) pour les agrégats de la
 * Phase 2 : antécédents, allergies, sérologies.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class MedicalPhase2AccessIntegrationTest {

    private static final UUID CENTER_ID = UUID.fromString("99994000-0000-0000-0000-000000000001");
    private static final UUID OTHER_CENTER_ID = UUID.fromString("99994000-0000-0000-0000-000000000002");
    private static final UUID PATIENT_ID = UUID.fromString("99994000-0000-0000-0000-000000000101");

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanup();
        seedPatient();
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM antecedents_medicaux WHERE patient_id = ?", PATIENT_ID);
        jdbc.update("DELETE FROM allergies_patient WHERE patient_id = ?", PATIENT_ID);
        jdbc.update("DELETE FROM serologies_patient WHERE patient_id = ?", PATIENT_ID);
        jdbc.update("DELETE FROM patients WHERE id = ?", PATIENT_ID);
    }

    @Test
    void medecin_should_create_and_read_antecedent() throws Exception {
        mockMvc.perform(post("/api/v1/patients/{id}/antecedents", PATIENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(antecedentBody(CENTER_ID))
                        .with(user(principal("medecin", CENTER_ID, "MEDECIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("N18.5"));

        mockMvc.perform(get("/api/v1/patients/{id}/antecedents", PATIENT_ID)
                        .param("centerId", CENTER_ID.toString())
                        .with(user(principal("medecin", CENTER_ID, "MEDECIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void admin_should_read_but_not_create_antecedent() throws Exception {
        mockMvc.perform(post("/api/v1/patients/{id}/antecedents", PATIENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(antecedentBody(CENTER_ID))
                        .with(user(principal("admin", CENTER_ID, "ADMIN"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/patients/{id}/antecedents", PATIENT_ID)
                        .param("centerId", CENTER_ID.toString())
                        .with(user(principal("admin", CENTER_ID, "ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void infirmier_should_not_access_antecedents() throws Exception {
        mockMvc.perform(get("/api/v1/patients/{id}/antecedents", PATIENT_ID)
                        .param("centerId", CENTER_ID.toString())
                        .with(user(principal("infirmier", CENTER_ID, "INFIRMIER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void medecin_of_other_center_should_not_read_antecedents() throws Exception {
        mockMvc.perform(get("/api/v1/patients/{id}/antecedents", PATIENT_ID)
                        .param("centerId", CENTER_ID.toString())
                        .with(user(principal("medecin", OTHER_CENTER_ID, "MEDECIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_should_reject_high_criticity_allergie_without_manifestations() throws Exception {
        String body = """
                {"centerId":"%s","codeSystem":"LOCAL","code":"PENICILLINE","codeDisplay":"Pénicilline",
                 "categorie":"MEDICAMENT","criticite":"HAUTE","typeReaction":"ALLERGIE",
                 "dateConstatation":"2026-01-01","statutVerification":"CONFIRMEE"}
                """.formatted(CENTER_ID);

        mockMvc.perform(post("/api/v1/patients/{id}/allergies", PATIENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(user(principal("medecin", CENTER_ID, "MEDECIN"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void create_should_reject_positive_serologie_without_conduite_a_tenir() throws Exception {
        String body = """
                {"centerId":"%s","marqueur":"AG_HBS","resultat":"POSITIF","datePrelevement":"2026-01-01",
                 "laboratoire":"Labo central"}
                """.formatted(CENTER_ID);

        mockMvc.perform(post("/api/v1/patients/{id}/serologies", PATIENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(user(principal("medecin", CENTER_ID, "MEDECIN"))))
                .andExpect(status().isUnprocessableEntity());
    }

    private String antecedentBody(UUID centerId) {
        return """
                {"centerId":"%s","type":"MEDICAL","codeSystem":"CIM10","code":"N18.5",
                 "codeDisplay":"IRC stade 5","dateDebut":"2020-01-01"}
                """.formatted(centerId);
    }

    private UserPrincipal principal(String username, UUID centerId, String role) {
        return UserPrincipal.create(
                UUID.randomUUID().toString(), centerId.toString(), username, "", List.of(role), true);
    }

    private void seedPatient() {
        jdbc.update(
                """
                        INSERT INTO patients (
                            id, center_id, code_patient, nom, prenom, sexe, date_admission,
                            numero_assurance, type_patient, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                PATIENT_ID, CENTER_ID, "PAT-MED2-001", "Medical", "PhaseDeux", "F",
                LocalDate.of(2026, 7, 1), "ASS-PAT-MED2-001", "NON_VACANCIER",
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }
}
