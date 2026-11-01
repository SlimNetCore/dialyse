package com.hemodialyse.backend.characterization.dossiermedical.constantes;

import com.hemodialyse.backend.characterization.gmao.plans.GoldenMaster;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class ConstantesDossierMedicalCharacterizationIntegrationTest {

    private static final java.nio.file.Path GOLDENS = java.nio.file.Path.of("src", "test", "resources", "characterization", "dossiermedical", "constantes");

    private static final UUID CENTRE_A = UUID.fromString("99997100-0000-0000-0000-00000000000a");
    private static final UUID CENTRE_B = UUID.fromString("99997100-0000-0000-0000-00000000000b");
    private static final UUID PATIENT_A = UUID.fromString("99997100-0000-0000-0000-000000000101");
    private static final UUID PATIENT_B = UUID.fromString("99997100-0000-0000-0000-000000000102");
    private static final UUID SEANCE_A = UUID.fromString("99997100-0000-0000-0000-000000000201");
    private static final UUID SEANCE_B = UUID.fromString("99997100-0000-0000-0000-000000000202");
    private static final UUID VOLET_A = UUID.fromString("99997100-0000-0000-0000-000000000301");
    private static final UUID USER_MED = UUID.fromString("99997100-0000-0000-0000-0000000000f1");

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanup();
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, type_patient, created_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                PATIENT_A, CENTRE_A, "PAT-DM1-A", "Patient", "A", "F", LocalDate.of(2026, 1, 10), "ASS-DM1-A", "NON_VACANCIER", OffsetDateTime.now(ZoneOffset.UTC));
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, type_patient, created_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                PATIENT_B, CENTRE_B, "PAT-DM1-B", "Patient", "B", "F", LocalDate.of(2026, 1, 10), "ASS-DM1-B", "NON_VACANCIER", OffsetDateTime.now(ZoneOffset.UTC));
        jdbc.update("INSERT INTO seances (id, center_id, patient_id, date_seance, statut, created_at) VALUES (?,?,?,?,?,?)",
                SEANCE_A, CENTRE_A, PATIENT_A, LocalDate.of(2026, 3, 1), "VALIDEE", OffsetDateTime.now(ZoneOffset.UTC));
        jdbc.update("INSERT INTO seances (id, center_id, patient_id, date_seance, statut, created_at) VALUES (?,?,?,?,?,?)",
                SEANCE_B, CENTRE_B, PATIENT_B, LocalDate.of(2026, 3, 2), "VALIDEE", OffsetDateTime.now(ZoneOffset.UTC));
        jdbc.update("INSERT INTO volet_paramedical (id, center_id, seance_id, poids_avant_kg, poids_apres_kg, ta_avant, ta_apres, debit_sang_ml_min, ultrafiltration_ml, duree_minutes, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                VOLET_A, CENTRE_A, SEANCE_A, 70.5, 68.0, "12/8", "11/7", 300, 2000, 240, OffsetDateTime.now(ZoneOffset.UTC));
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM volet_paramedical WHERE center_id IN (?, ?)", CENTRE_A, CENTRE_B);
        jdbc.update("DELETE FROM seances WHERE center_id IN (?, ?)", CENTRE_A, CENTRE_B);
        jdbc.update("DELETE FROM patients WHERE center_id IN (?, ?)", CENTRE_A, CENTRE_B);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor medecin(UUID centre) {
        return user(UserPrincipal.create(USER_MED.toString(), centre.toString(), "medecin", "", List.of("MEDECIN"), true));
    }

    private String normalize(String raw) {
        raw = raw.replaceAll("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?Z?", "<TIMESTAMP>");
        java.util.Map<String, String> aliases = new java.util.LinkedHashMap<>();
        aliases.put(CENTRE_A.toString(), "<CENTRE_A>");
        aliases.put(CENTRE_B.toString(), "<CENTRE_B>");
        aliases.put(PATIENT_A.toString(), "<PATIENT_A>");
        aliases.put(PATIENT_B.toString(), "<PATIENT_B>");
        aliases.put(SEANCE_A.toString(), "<SEANCE_A>");
        aliases.put(SEANCE_B.toString(), "<SEANCE_B>");
        aliases.put(VOLET_A.toString(), "<VOLET_A>");
        java.util.regex.Pattern UUID_RE = java.util.regex.Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        int[] unknown = {0};
        java.util.regex.Matcher m = UUID_RE.matcher(raw);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String u = m.group();
            String a = aliases.computeIfAbsent(u, k -> "<UUID_GENERE_" + (++unknown[0]) + ">");
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(a));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    @Test
    void lecture_constantes_centre_A_avec_donnees() throws Exception {
        MvcResult r = mvc.perform(get("/api/v1/patients/{id}/constantes", PATIENT_A)
                        .param("centerId", CENTRE_A.toString())
                        .param("page", "0")
                        .param("size", "5")
                        .with(medecin(CENTRE_A)))
                .andExpect(status().isOk())
                .andReturn();
        GoldenMaster.verify(GOLDENS, "constantes_centre_A_page", normalize(r.getResponse().getContentAsString()));
    }

    @Test
    void lecture_constantes_vide_centre_B_patient_B() throws Exception {
        MvcResult r = mvc.perform(get("/api/v1/patients/{id}/constantes", PATIENT_B)
                        .param("centerId", CENTRE_B.toString())
                        .param("page", "0")
                        .param("size", "5")
                        .with(medecin(CENTRE_B)))
                .andExpect(status().isOk())
                .andReturn();
        GoldenMaster.verify(GOLDENS, "constantes_centre_B_vide", normalize(r.getResponse().getContentAsString()));
    }

    @Test
    void cross_centre_refuse() throws Exception {
        MvcResult r = mvc.perform(get("/api/v1/patients/{id}/constantes", PATIENT_A)
                        .param("centerId", CENTRE_A.toString())
                        .with(medecin(CENTRE_B)))
                .andExpect(status().isForbidden())
                .andReturn();
        GoldenMaster.verify(GOLDENS, "constantes_cross_centre", normalize(r.getResponse().getContentAsString()));
    }

    @Test
    void infirmier_refuse() throws Exception {
        MvcResult r = mvc.perform(get("/api/v1/patients/{id}/constantes", PATIENT_A)
                        .param("centerId", CENTRE_A.toString())
                        .with(user(UserPrincipal.create("inf", CENTRE_A.toString(), "infirmier", "", List.of("INFIRMIER"), true))))
                .andExpect(status().isForbidden())
                .andReturn();
        GoldenMaster.verify(GOLDENS, "constantes_infirmier_refuse", normalize(r.getResponse().getContentAsString()));
    }
}