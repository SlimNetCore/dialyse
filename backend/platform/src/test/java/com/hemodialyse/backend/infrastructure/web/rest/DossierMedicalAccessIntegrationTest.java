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
 * Matrice d'accès du dossier médical, et isolation multi-centres (AGENTS.md §2 et §7).
 * <p>
 * Les {@code @PreAuthorize} ne sont pas exercés par les tests unitaires de contrôleur : seule une
 * pile Spring complète les évalue réellement. Ce test est donc le seul garant de la règle
 * « le médecin écrit, l'administrateur consulte, l'infirmier ne voit que ce qui relève du soin ».
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class DossierMedicalAccessIntegrationTest {

    private static final UUID CENTER_ID = UUID.fromString("99993000-0000-0000-0000-000000000001");
    private static final UUID OTHER_CENTER_ID = UUID.fromString("99993000-0000-0000-0000-000000000002");
    private static final UUID PATIENT_ID = UUID.fromString("99993000-0000-0000-0000-000000000101");

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        cleanup();
        seedPatient();
        seedDossier();
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM dossier_medical_patient WHERE patient_id = ?", PATIENT_ID);
        jdbc.update("DELETE FROM prescriptions_medicales WHERE patient_id = ?", PATIENT_ID);
        jdbc.update("DELETE FROM patients WHERE id = ?", PATIENT_ID);
    }

    @Test
    void medecin_should_read_and_write_dossier() throws Exception {
        mockMvc.perform(get("/api/v1/patients/{id}/dossier-medical", PATIENT_ID)
                        .param("centerId", CENTER_ID.toString())
                        .with(user(principal("medecin", CENTER_ID, "MEDECIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nephropathieInitiale").value("Nephropathie diabetique"));

        mockMvc.perform(post("/api/v1/patients/{id}/dossier-medical", PATIENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(upsertBody(CENTER_ID))
                        .with(user(principal("medecin", CENTER_ID, "MEDECIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void admin_should_read_but_not_write_dossier() throws Exception {
        mockMvc.perform(get("/api/v1/patients/{id}/dossier-medical", PATIENT_ID)
                        .param("centerId", CENTER_ID.toString())
                        .with(user(principal("admin", CENTER_ID, "ADMIN"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/patients/{id}/dossier-medical", PATIENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(upsertBody(CENTER_ID))
                        .with(user(principal("admin", CENTER_ID, "ADMIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void infirmier_should_read_dossier_for_care_safety_but_not_prescriptions() throws Exception {
        // Le statut hépatite conditionne l'isolation machine : l'infirmier doit pouvoir le lire.
        mockMvc.perform(get("/api/v1/patients/{id}/dossier-medical", PATIENT_ID)
                        .param("centerId", CENTER_ID.toString())
                        .with(user(principal("infirmier", CENTER_ID, "INFIRMIER"))))
                .andExpect(status().isOk());

        // Les prescriptions restent hors de son périmètre.
        mockMvc.perform(get("/api/v1/patients/{id}/prescriptions", PATIENT_ID)
                        .param("centerId", CENTER_ID.toString())
                        .with(user(principal("infirmier", CENTER_ID, "INFIRMIER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void secretaire_should_not_read_dossier() throws Exception {
        mockMvc.perform(get("/api/v1/patients/{id}/dossier-medical", PATIENT_ID)
                        .param("centerId", CENTER_ID.toString())
                        .with(user(principal("secretaire", CENTER_ID, "SECRETAIRE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void medecin_should_not_read_dossier_of_another_center() throws Exception {
        // Le centre demandé ne correspond pas à celui du jeton : refus, même si le patient existe.
        mockMvc.perform(get("/api/v1/patients/{id}/dossier-medical", PATIENT_ID)
                        .param("centerId", CENTER_ID.toString())
                        .with(user(principal("medecin", OTHER_CENTER_ID, "MEDECIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void medecin_should_not_write_dossier_of_another_center() throws Exception {
        mockMvc.perform(post("/api/v1/patients/{id}/dossier-medical", PATIENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(upsertBody(CENTER_ID))
                        .with(user(principal("medecin", OTHER_CENTER_ID, "MEDECIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void prescriptions_list_should_be_paged() throws Exception {
        mockMvc.perform(get("/api/v1/patients/{id}/prescriptions", PATIENT_ID)
                        .param("centerId", CENTER_ID.toString())
                        .param("page", "0")
                        .param("size", "10")
                        .with(user(principal("medecin", CENTER_ID, "MEDECIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.total").exists())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10));
    }

    private UserPrincipal principal(String username, UUID centerId, String role) {
        return UserPrincipal.create(
                UUID.randomUUID().toString(), centerId.toString(), username, "", List.of(role), true);
    }

    private String upsertBody(UUID centerId) {
        return """
                {
                  "centerId": "%s",
                  "nephropathieInitiale": "Nephropathie diabetique",
                  "dateMiseEnDialyse": "2024-03-15",
                  "hepatiteBStatut": "VACCINE",
                  "hepatiteCStatut": "NEGATIF",
                  "observationGlobale": "RAS"
                }
                """.formatted(centerId);
    }

    private void seedPatient() {
        jdbc.update(
                """
                        INSERT INTO patients (
                            id, center_id, code_patient, nom, prenom, sexe, date_admission,
                            numero_assurance, type_patient, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                PATIENT_ID,
                CENTER_ID,
                "PAT-DOSSIER-001",
                "Dossier",
                "Medical",
                "M",
                LocalDate.of(2026, 7, 1),
                "ASS-PAT-DOSSIER-001",
                "NON_VACANCIER",
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    private void seedDossier() {
        jdbc.update(
                """
                        INSERT INTO dossier_medical_patient (
                            id, patient_id, center_id, nephropathie_initiale, date_mise_en_dialyse,
                            hepatite_b_statut, hepatite_c_statut, observation_globale, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID(),
                PATIENT_ID,
                CENTER_ID,
                "Nephropathie diabetique",
                LocalDate.of(2024, 3, 15),
                "VACCINE",
                "NEGATIF",
                "RAS",
                OffsetDateTime.now(ZoneOffset.UTC),
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }
}
