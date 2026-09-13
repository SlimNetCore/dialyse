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
 * Matrice d'accès et machine à états (AGENTS.md §2 et §7) pour la Phase 3 : demandes d'examen
 * et observations biologiques LOINC.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class MedicalPhase3AccessIntegrationTest {

    private static final UUID CENTER_ID = UUID.fromString("99995000-0000-0000-0000-000000000001");
    private static final UUID OTHER_CENTER_ID = UUID.fromString("99995000-0000-0000-0000-000000000002");
    private static final UUID PATIENT_ID = UUID.fromString("99995000-0000-0000-0000-000000000101");

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
        jdbc.update("DELETE FROM observations_biologiques WHERE patient_id = ?", PATIENT_ID);
        jdbc.update("DELETE FROM lignes_demande_examen WHERE demande_id IN "
                + "(SELECT id FROM demandes_examen WHERE patient_id = ?)", PATIENT_ID);
        jdbc.update("DELETE FROM demandes_examen WHERE patient_id = ?", PATIENT_ID);
        jdbc.update("DELETE FROM patients WHERE id = ?", PATIENT_ID);
    }

    @Test
    void medecin_should_create_demande_and_progress_through_lifecycle() throws Exception {
        String createResponse = mockMvc.perform(post("/api/v1/patients/{id}/demandes-examen", PATIENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(demandeBody(CENTER_ID))
                        .with(user(principal("medecin", CENTER_ID, "MEDECIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("DEMANDE"))
                .andReturn().getResponse().getContentAsString();

        UUID demandeId = extractId(createResponse);

        mockMvc.perform(put("/api/v1/patients/{id}/demandes-examen/{did}/preleve", PATIENT_ID, demandeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transitionBody(CENTER_ID, null))
                        .with(user(principal("medecin", CENTER_ID, "MEDECIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("PRELEVE"));

        mockMvc.perform(put("/api/v1/patients/{id}/demandes-examen/{did}/valider", PATIENT_ID, demandeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transitionBody(CENTER_ID, "RAS"))
                        .with(user(principal("medecin", CENTER_ID, "MEDECIN"))))
                .andExpect(status().isUnprocessableEntity()); // RESULTAT_DISPONIBLE requis avant VALIDE
    }

    @Test
    void admin_should_read_but_not_create_demande() throws Exception {
        mockMvc.perform(post("/api/v1/patients/{id}/demandes-examen", PATIENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(demandeBody(CENTER_ID))
                        .with(user(principal("admin", CENTER_ID, "ADMIN"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/patients/{id}/demandes-examen", PATIENT_ID)
                        .param("centerId", CENTER_ID.toString())
                        .with(user(principal("admin", CENTER_ID, "ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void medecin_of_other_center_should_not_read_demandes() throws Exception {
        mockMvc.perform(get("/api/v1/patients/{id}/demandes-examen", PATIENT_ID)
                        .param("centerId", CENTER_ID.toString())
                        .with(user(principal("medecin", OTHER_CENTER_ID, "MEDECIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_demande_should_reject_empty_lignes() throws Exception {
        String body = """
                {"centerId":"%s","prescripteurId":"medecin-1","dateDemande":"2026-01-01",
                 "categorie":"BIOLOGIE","urgent":false,"motif":"Bilan","lignes":[]}
                """.formatted(CENTER_ID);

        mockMvc.perform(post("/api/v1/patients/{id}/demandes-examen", PATIENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(user(principal("medecin", CENTER_ID, "MEDECIN"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void observation_should_reject_non_loinc_system() throws Exception {
        String body = """
                {"centerId":"%s","codeSystem":"LOCAL","code":"TRUC","codeDisplay":"Truc",
                 "valeurNum":10,"unite":"g/dL","datePrelevement":"2026-01-01"}
                """.formatted(CENTER_ID);

        mockMvc.perform(post("/api/v1/patients/{id}/observations", PATIENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(user(principal("medecin", CENTER_ID, "MEDECIN"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void observation_should_be_paginated() throws Exception {
        mockMvc.perform(get("/api/v1/patients/{id}/observations", PATIENT_ID)
                        .param("centerId", CENTER_ID.toString())
                        .param("page", "0")
                        .param("size", "10")
                        .with(user(principal("medecin", CENTER_ID, "MEDECIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10));
    }

    private UUID extractId(String json) {
        var matcher = java.util.regex.Pattern.compile("\"id\":\"([0-9a-fA-F-]{36})\"").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("Aucun id trouvé dans la réponse : " + json);
        }
        return UUID.fromString(matcher.group(1));
    }

    private String demandeBody(UUID centerId) {
        return """
                {"centerId":"%s","prescripteurId":"medecin-1","dateDemande":"2026-01-01",
                 "categorie":"BIOLOGIE","urgent":false,"motif":"Bilan mensuel",
                 "lignes":[{"codeSystem":"LOINC","code":"718-7","codeDisplay":"Hémoglobine"}]}
                """.formatted(centerId);
    }

    private String transitionBody(UUID centerId, String conclusion) {
        return """
                {"centerId":"%s","conclusion":%s}
                """.formatted(centerId, conclusion == null ? "null" : "\"" + conclusion + "\"");
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
                PATIENT_ID, CENTER_ID, "PAT-MED3-001", "Medical", "PhaseTrois", "M",
                LocalDate.of(2026, 7, 1), "ASS-PAT-MED3-001", "NON_VACANCIER",
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }
}
