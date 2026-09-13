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
 * Matrice d'accès (AGENTS.md §2 et §7) pour la Phase 4 : administrations réelles du traitement de
 * l'anémie, suivi agrégé (Hb/ferritine/KDIGO) et vue longitudinale des constantes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class MedicalPhase4AccessIntegrationTest {

    private static final UUID CENTER_ID = UUID.fromString("99996000-0000-0000-0000-000000000001");
    private static final UUID OTHER_CENTER_ID = UUID.fromString("99996000-0000-0000-0000-000000000002");
    private static final UUID PATIENT_ID = UUID.fromString("99996000-0000-0000-0000-000000000101");

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
        jdbc.update("DELETE FROM administrations_anemie WHERE patient_id = ?", PATIENT_ID);
        jdbc.update("DELETE FROM patients WHERE id = ?", PATIENT_ID);
    }

    @Test
    void medecin_should_create_administration() throws Exception {
        mockMvc.perform(post("/api/v1/patients/{id}/administrations-anemie", PATIENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(administrationBody(CENTER_ID, true, null))
                        .with(user(principal("medecin", CENTER_ID, "MEDECIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.administree").value(true));
    }

    @Test
    void create_administration_should_reject_missing_dose_when_administered() throws Exception {
        String body = """
                {"centerId":"%s","prescriptionMedicaleId":null,"typeTraitement":"EPO",
                 "molecule":"Darbepoetine","dose":null,"uniteDose":null,"voie":"SC",
                 "dateAdministration":"2026-01-01","seanceId":null,"administrePar":"infirmier-1",
                 "administree":true,"motifNonAdministration":null}
                """.formatted(CENTER_ID);

        mockMvc.perform(post("/api/v1/patients/{id}/administrations-anemie", PATIENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(user(principal("medecin", CENTER_ID, "MEDECIN"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void admin_should_read_but_not_create_administration() throws Exception {
        mockMvc.perform(post("/api/v1/patients/{id}/administrations-anemie", PATIENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(administrationBody(CENTER_ID, true, null))
                        .with(user(principal("admin", CENTER_ID, "ADMIN"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/patients/{id}/administrations-anemie", PATIENT_ID)
                        .param("centerId", CENTER_ID.toString())
                        .with(user(principal("admin", CENTER_ID, "ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void infirmier_should_not_access_administrations() throws Exception {
        mockMvc.perform(get("/api/v1/patients/{id}/administrations-anemie", PATIENT_ID)
                        .param("centerId", CENTER_ID.toString())
                        .with(user(principal("infirmier", CENTER_ID, "INFIRMIER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void medecin_of_other_center_should_not_read_administrations() throws Exception {
        mockMvc.perform(get("/api/v1/patients/{id}/administrations-anemie", PATIENT_ID)
                        .param("centerId", CENTER_ID.toString())
                        .with(user(principal("medecin", OTHER_CENTER_ID, "MEDECIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void administrations_should_be_paginated() throws Exception {
        mockMvc.perform(get("/api/v1/patients/{id}/administrations-anemie", PATIENT_ID)
                        .param("centerId", CENTER_ID.toString())
                        .param("page", "0")
                        .param("size", "10")
                        .with(user(principal("medecin", CENTER_ID, "MEDECIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10));
    }

    @Test
    void medecin_should_read_suivi_anemie() throws Exception {
        mockMvc.perform(get("/api/v1/patients/{id}/suivi-anemie", PATIENT_ID)
                        .param("centerId", CENTER_ID.toString())
                        .with(user(principal("medecin", CENTER_ID, "MEDECIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluations").isArray());
    }

    @Test
    void infirmier_should_not_read_suivi_anemie() throws Exception {
        mockMvc.perform(get("/api/v1/patients/{id}/suivi-anemie", PATIENT_ID)
                        .param("centerId", CENTER_ID.toString())
                        .with(user(principal("infirmier", CENTER_ID, "INFIRMIER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void medecin_of_other_center_should_not_read_suivi_anemie() throws Exception {
        mockMvc.perform(get("/api/v1/patients/{id}/suivi-anemie", PATIENT_ID)
                        .param("centerId", CENTER_ID.toString())
                        .with(user(principal("medecin", OTHER_CENTER_ID, "MEDECIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void medecin_and_admin_should_read_constantes_paginated() throws Exception {
        mockMvc.perform(get("/api/v1/patients/{id}/constantes", PATIENT_ID)
                        .param("centerId", CENTER_ID.toString())
                        .param("page", "0")
                        .param("size", "5")
                        .with(user(principal("medecin", CENTER_ID, "MEDECIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.size").value(5));

        mockMvc.perform(get("/api/v1/patients/{id}/constantes", PATIENT_ID)
                        .param("centerId", CENTER_ID.toString())
                        .with(user(principal("admin", CENTER_ID, "ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void infirmier_should_not_read_constantes() throws Exception {
        mockMvc.perform(get("/api/v1/patients/{id}/constantes", PATIENT_ID)
                        .param("centerId", CENTER_ID.toString())
                        .with(user(principal("infirmier", CENTER_ID, "INFIRMIER"))))
                .andExpect(status().isForbidden());
    }

    private String administrationBody(UUID centerId, boolean administree, String motif) {
        return """
                {"centerId":"%s","prescriptionMedicaleId":null,"typeTraitement":"EPO",
                 "molecule":"Darbepoetine","dose":%s,"uniteDose":%s,"voie":"SC",
                 "dateAdministration":"2026-01-01","seanceId":null,"administrePar":"infirmier-1",
                 "administree":%s,"motifNonAdministration":%s}
                """.formatted(centerId,
                administree ? "60" : "null",
                administree ? "\"UI\"" : "null",
                administree,
                motif == null ? "null" : "\"" + motif + "\"");
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
                PATIENT_ID, CENTER_ID, "PAT-MED4-001", "Medical", "PhaseQuatre", "F",
                LocalDate.of(2026, 7, 1), "ASS-PAT-MED4-001", "NON_VACANCIER",
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }
}
