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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Matrice d'accès (AGENTS.md §2 et §7) pour la Phase 5 : ordonnances médicamenteuses (cycle de
 * signature) et export FHIR du dossier médical.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class MedicalPhase5AccessIntegrationTest {

    private static final UUID CENTER_ID = UUID.fromString("99997000-0000-0000-0000-000000000001");
    private static final UUID OTHER_CENTER_ID = UUID.fromString("99997000-0000-0000-0000-000000000002");
    private static final UUID PATIENT_ID = UUID.fromString("99997000-0000-0000-0000-000000000101");

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
        jdbc.update("DELETE FROM lignes_ordonnance WHERE ordonnance_id IN "
                + "(SELECT id FROM ordonnances WHERE patient_id = ?)", PATIENT_ID);
        jdbc.update("DELETE FROM ordonnances WHERE patient_id = ?", PATIENT_ID);
        jdbc.update("DELETE FROM app_settings WHERE center_id IN (?, ?) AND cle = 'SEQ_ORD'", CENTER_ID, OTHER_CENTER_ID);
        jdbc.update("DELETE FROM patients WHERE id = ?", PATIENT_ID);
    }

    @Test
    void medecin_should_create_and_sign_and_print_ordonnance() throws Exception {
        String createResponse = mockMvc.perform(post("/api/v1/patients/{id}/ordonnances", PATIENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ordonnanceBody(CENTER_ID))
                        .with(user(principal("medecin", CENTER_ID, "MEDECIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("BROUILLON"))
                .andExpect(jsonPath("$.numero").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        UUID ordonnanceId = extractId(createResponse);

        mockMvc.perform(put("/api/v1/patients/{id}/ordonnances/{oid}/signer", PATIENT_ID, ordonnanceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transitionBody(CENTER_ID))
                        .with(user(principal("medecin", CENTER_ID, "MEDECIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("SIGNEE"))
                .andExpect(jsonPath("$.numero").value("ORD-00001"));

        mockMvc.perform(put("/api/v1/patients/{id}/ordonnances/{oid}/imprimer", PATIENT_ID, ordonnanceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transitionBody(CENTER_ID))
                        .with(user(principal("medecin", CENTER_ID, "MEDECIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("IMPRIMEE"));

        mockMvc.perform(put("/api/v1/patients/{id}/ordonnances/{oid}/signer", PATIENT_ID, ordonnanceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transitionBody(CENTER_ID))
                        .with(user(principal("medecin", CENTER_ID, "MEDECIN"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void create_ordonnance_should_reject_empty_lignes() throws Exception {
        String body = """
                {"centerId":"%s","medecinId":"medecin-1","datePrescription":"2026-01-01","lignes":[]}
                """.formatted(CENTER_ID);

        mockMvc.perform(post("/api/v1/patients/{id}/ordonnances", PATIENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(user(principal("medecin", CENTER_ID, "MEDECIN"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void admin_should_read_but_not_create_ordonnance() throws Exception {
        mockMvc.perform(post("/api/v1/patients/{id}/ordonnances", PATIENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ordonnanceBody(CENTER_ID))
                        .with(user(principal("admin", CENTER_ID, "ADMIN"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/patients/{id}/ordonnances", PATIENT_ID)
                        .param("centerId", CENTER_ID.toString())
                        .with(user(principal("admin", CENTER_ID, "ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void infirmier_should_not_access_ordonnances() throws Exception {
        mockMvc.perform(get("/api/v1/patients/{id}/ordonnances", PATIENT_ID)
                        .param("centerId", CENTER_ID.toString())
                        .with(user(principal("infirmier", CENTER_ID, "INFIRMIER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void medecin_of_other_center_should_not_read_ordonnances() throws Exception {
        mockMvc.perform(get("/api/v1/patients/{id}/ordonnances", PATIENT_ID)
                        .param("centerId", CENTER_ID.toString())
                        .with(user(principal("medecin", OTHER_CENTER_ID, "MEDECIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void ordonnances_should_be_paginated() throws Exception {
        mockMvc.perform(get("/api/v1/patients/{id}/ordonnances", PATIENT_ID)
                        .param("centerId", CENTER_ID.toString())
                        .param("page", "0")
                        .param("size", "5")
                        .with(user(principal("medecin", CENTER_ID, "MEDECIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.size").value(5));
    }

    @Test
    void medecin_should_export_fhir_bundle() throws Exception {
        mockMvc.perform(get("/api/v1/patients/{id}/dossier-medical/export-fhir", PATIENT_ID)
                        .param("centerId", CENTER_ID.toString())
                        .with(user(principal("medecin", CENTER_ID, "MEDECIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceType").value("Bundle"))
                .andExpect(jsonPath("$.entry").isArray());
    }

    @Test
    void admin_should_not_export_fhir_bundle() throws Exception {
        mockMvc.perform(get("/api/v1/patients/{id}/dossier-medical/export-fhir", PATIENT_ID)
                        .param("centerId", CENTER_ID.toString())
                        .with(user(principal("admin", CENTER_ID, "ADMIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void medecin_of_other_center_should_not_export_fhir_bundle() throws Exception {
        mockMvc.perform(get("/api/v1/patients/{id}/dossier-medical/export-fhir", PATIENT_ID)
                        .param("centerId", CENTER_ID.toString())
                        .with(user(principal("medecin", OTHER_CENTER_ID, "MEDECIN"))))
                .andExpect(status().isForbidden());
    }

    private UUID extractId(String json) {
        var matcher = java.util.regex.Pattern.compile("\"id\":\"([0-9a-fA-F-]{36})\"").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("Aucun id trouvé dans la réponse : " + json);
        }
        return UUID.fromString(matcher.group(1));
    }

    private String ordonnanceBody(UUID centerId) {
        return """
                {"centerId":"%s","medecinId":"medecin-1","datePrescription":"2026-01-01",
                 "lignes":[{"codeSystem":"ATC","code":"B03XA02","codeDisplay":"Darbepoetine alfa",
                 "posologie":"1 injection SC / semaine","voie":"SC","dureeJours":28,"quantite":4}]}
                """.formatted(centerId);
    }

    private String transitionBody(UUID centerId) {
        return """
                {"centerId":"%s"}
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
                PATIENT_ID, CENTER_ID, "PAT-MED5-001", "Medical", "PhaseCinq", "M",
                LocalDate.of(2026, 7, 1), "ASS-PAT-MED5-001", "NON_VACANCIER",
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }
}
