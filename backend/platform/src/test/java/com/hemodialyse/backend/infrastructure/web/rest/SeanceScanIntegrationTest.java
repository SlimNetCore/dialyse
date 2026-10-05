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

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class SeanceScanIntegrationTest {

    private static final UUID CENTER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PATIENT_ID = UUID.fromString("10000000-0000-0000-0000-000000009991");
    private static final UUID ATTESTATION_ID = UUID.fromString("10000000-0000-0000-0000-000000009981");
    private static final UUID PEC_ID = UUID.fromString("10000000-0000-0000-0000-000000009982");
    private static final UUID FORFAIT_ID = UUID.fromString("f0000001-0000-0000-0000-000000000001");
    private static final String NON_BILLABLE_ERROR = "Le patient doit avoir une prise en charge valide pour être facturé";

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
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM seances WHERE patient_id = ? AND center_id = ?", PATIENT_ID, CENTER_ID);
        jdbc.update("DELETE FROM prise_en_charge WHERE patient_id = ? AND center_id = ?", PATIENT_ID, CENTER_ID);
        jdbc.update("DELETE FROM attestation_droit WHERE patient_id = ? AND center_id = ?", PATIENT_ID, CENTER_ID);
        jdbc.update("DELETE FROM patients WHERE id = ?", PATIENT_ID);
    }

    private static String scanPayload() {
        return """
                {
                  "centerId": "%s",
                  "qrCode": "PAT-SCAN-NULL-GEN"
                }
                """.formatted(CENTER_ID);
    }

    @Test
    void scan_should_return_200_when_patient_has_no_generateur() throws Exception {
        cleanup();
        seedBillablePatientWithoutGenerateur();

        String payload = """
                {
                  "centerId": "%s",
                  "qrCode": "PAT-SCAN-NULL-GEN"
                }
                """.formatted(CENTER_ID);

        mockMvc.perform(post("/api/v1/seances/scan")
                        .with(user("infirmer-01").roles("INFIRMIER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("VALIDEE"))
                .andExpect(jsonPath("$.created").value(true))
                .andExpect(jsonPath("$.validatedNow").value(true))
                .andExpect(jsonPath("$.alreadyValidated").value(false))
                .andExpect(jsonPath("$.generateurId").value(nullValue()))
                .andExpect(jsonPath("$.generateurNom").value(nullValue()))
                .andExpect(jsonPath("$.generateurMarque").value(nullValue()))
                .andExpect(jsonPath("$.generateurEtat").value(nullValue()));

        Long createdSeances = jdbc.queryForObject(
                "SELECT COUNT(1) FROM seances WHERE center_id = ? AND patient_id = ?",
                Long.class,
                CENTER_ID,
                PATIENT_ID
        );
        org.junit.jupiter.api.Assertions.assertEquals(1L, createdSeances == null ? 0L : createdSeances);
        org.junit.jupiter.api.Assertions.assertEquals("infirmer-01", jdbc.queryForObject(
                "SELECT signed_infirmier_by FROM seances WHERE center_id = ? AND patient_id = ?", String.class,
                CENTER_ID, PATIENT_ID), "l'infirmier qui scanne signe la validation");
    }

    @Test
    void a_second_nurse_scan_returns_the_same_validated_session_without_reprocessing_it() throws Exception {
        cleanup();
        seedBillablePatientWithoutGenerateur();
        String payload = scanPayload();

        mockMvc.perform(post("/api/v1/seances/scan").with(user("infirmer-01").roles("INFIRMIER"))
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/seances/scan").with(user("infirmer-02").roles("INFIRMIER"))
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDEE"))
                .andExpect(jsonPath("$.created").value(false))
                .andExpect(jsonPath("$.validatedNow").value(false))
                .andExpect(jsonPath("$.alreadyValidated").value(true));

        org.junit.jupiter.api.Assertions.assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(1) FROM seances WHERE center_id = ? AND patient_id = ?", Long.class, CENTER_ID, PATIENT_ID));
        org.junit.jupiter.api.Assertions.assertEquals("infirmer-01", jdbc.queryForObject(
                "SELECT signed_infirmier_by FROM seances WHERE center_id = ? AND patient_id = ?", String.class,
                CENTER_ID, PATIENT_ID), "la signature du premier scan est conservée");
    }

    @Test
    void a_nurse_scan_validates_the_created_session_of_the_secretary_instead_of_duplicating_it() throws Exception {
        cleanup();
        seedBillablePatientWithoutGenerateur();
        String payload = scanPayload();

        mockMvc.perform(post("/api/v1/seances/scan").with(user("secretaire-01").roles("SECRETAIRE"))
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CREE"))
                .andExpect(jsonPath("$.validatedNow").value(false));
        mockMvc.perform(post("/api/v1/seances/scan").with(user("infirmer-01").roles("INFIRMIER"))
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDEE"))
                .andExpect(jsonPath("$.created").value(false))
                .andExpect(jsonPath("$.validatedNow").value(true));

        org.junit.jupiter.api.Assertions.assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(1) FROM seances WHERE center_id = ? AND patient_id = ?", Long.class, CENTER_ID, PATIENT_ID));
    }

    @Test
    void scan_should_return_422_when_patient_is_not_billable() throws Exception {
        cleanup();
        seedPatientOnlyWithoutBillingEligibility();

        String payload = """
                {
                  "centerId": "%s",
                  "qrCode": "PAT-SCAN-NULL-GEN"
                }
                """.formatted(CENTER_ID);

        mockMvc.perform(post("/api/v1/seances/scan")
                        .with(user("infirmer-01").roles("INFIRMIER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(NON_BILLABLE_ERROR));

        Long createdSeances = jdbc.queryForObject(
                "SELECT COUNT(1) FROM seances WHERE center_id = ? AND patient_id = ?",
                Long.class,
                CENTER_ID,
                PATIENT_ID
        );
        org.junit.jupiter.api.Assertions.assertEquals(0L, createdSeances == null ? 0L : createdSeances);
    }

    private void seedBillablePatientWithoutGenerateur() {
        seedPatientOnlyWithoutBillingEligibility();
        jdbc.update(
                """
                        INSERT INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin)
                        VALUES (?, ?, ?, DATE '2024-01-01', DATE '2030-12-31')
                        """,
                ATTESTATION_ID,
                PATIENT_ID,
                CENTER_ID
        );
        jdbc.update(
                """
                        INSERT INTO prise_en_charge (
                            id, patient_id, center_id,
                            date_debut_demande, date_fin_demande, forfait_demande_id,
                            date_debut_effectif, date_fin_effectif, forfait_effectif_id,
                            statut
                        ) VALUES (?, ?, ?, DATE '2024-01-01', DATE '2030-12-31', ?, DATE '2024-01-01', DATE '2030-12-31', ?, 'VALIDEE')
                        """,
                PEC_ID,
                PATIENT_ID,
                CENTER_ID,
                FORFAIT_ID,
                FORFAIT_ID
        );
    }

    /**
     * Colonne « jour de dialyse » du patient correspondant à aujourd'hui.
     */
    private static String colonneAujourdhui() {
        return switch (LocalDate.now().getDayOfWeek()) {
            case MONDAY -> "jour_lundi";
            case TUESDAY -> "jour_mardi";
            case WEDNESDAY -> "jour_mercredi";
            case THURSDAY -> "jour_jeudi";
            case FRIDAY -> "jour_vendredi";
            case SATURDAY -> "jour_samedi";
            case SUNDAY -> "jour_dimanche";
        };
    }

    private static String scanPayloadAvecMotif(String motif, String precision) {
        return """
                {"centerId": "%s", "qrCode": "PAT-SCAN-NULL-GEN", "motifHorsPlanning": %s,
                 "precisionHorsPlanning": %s}
                """.formatted(CENTER_ID, motif == null ? "null" : "\"" + motif + "\"",
                precision == null ? "null" : "\"" + precision + "\"");
    }

    private void seedPatientOnlyWithoutBillingEligibility() {
        jdbc.update(
                """
                        INSERT INTO patients (
                            id, center_id, code_patient, nom, prenom, sexe, date_admission,
                            numero_assurance, type_patient, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, CURRENT_DATE, ?, ?, ?)
                        """,
                PATIENT_ID,
                CENTER_ID,
                "PAT-SCAN-NULL-GEN",
                "Patient",
                "SansGenerateur",
                "M",
                "ASS-SCAN-NULL-GEN",
                "NON_VACANCIER",
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        definirJourDeDialyseAujourdhui(true);
    }

    private void definirJourDeDialyseAujourdhui(boolean programme) {
        jdbc.update("UPDATE patients SET " + colonneAujourdhui() + " = ? WHERE id = ?", programme, PATIENT_ID);
    }

    @Test
    void scan_of_a_patient_not_scheduled_today_asks_for_a_confirmation_and_creates_nothing() throws Exception {
        cleanup();
        seedBillablePatientWithoutGenerateur();
        definirJourDeDialyseAujourdhui(false);

        mockMvc.perform(post("/api/v1/seances/scan").with(user("infirmer-01").roles("INFIRMIER"))
                        .contentType(MediaType.APPLICATION_JSON).content(scanPayload()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SEANCE_HORS_PLANNING_JOUR"));

        org.junit.jupiter.api.Assertions.assertEquals(0L, jdbc.queryForObject(
                "SELECT COUNT(1) FROM seances WHERE center_id = ? AND patient_id = ?", Long.class, CENTER_ID, PATIENT_ID));
    }

    @Test
    void the_nurse_confirmation_with_a_motive_creates_a_flagged_validated_session() throws Exception {
        cleanup();
        seedBillablePatientWithoutGenerateur();
        definirJourDeDialyseAujourdhui(false);

        mockMvc.perform(post("/api/v1/seances/scan").with(user("infirmer-01").roles("INFIRMIER"))
                        .contentType(MediaType.APPLICATION_JSON).content(scanPayloadAvecMotif("RATTRAPAGE", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDEE"))
                .andExpect(jsonPath("$.horsPlanning").value(true))
                .andExpect(jsonPath("$.motifHorsPlanning").value("RATTRAPAGE"));

        org.junit.jupiter.api.Assertions.assertEquals("RATTRAPAGE", jdbc.queryForObject(
                "SELECT motif_hors_planning FROM seances WHERE center_id = ? AND patient_id = ?", String.class,
                CENTER_ID, PATIENT_ID));
    }

    @Test
    void the_motive_other_requires_a_precision() throws Exception {
        cleanup();
        seedBillablePatientWithoutGenerateur();
        definirJourDeDialyseAujourdhui(false);

        mockMvc.perform(post("/api/v1/seances/scan").with(user("infirmer-01").roles("INFIRMIER"))
                        .contentType(MediaType.APPLICATION_JSON).content(scanPayloadAvecMotif("AUTRE", null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SEANCE_DEROGATION_PRECISION_REQUISE"));
    }

    @Test
    void the_secretary_cannot_confirm_an_out_of_planning_scan() throws Exception {
        cleanup();
        seedBillablePatientWithoutGenerateur();
        definirJourDeDialyseAujourdhui(false);

        mockMvc.perform(post("/api/v1/seances/scan").with(user("secretaire-01").roles("SECRETAIRE"))
                        .contentType(MediaType.APPLICATION_JSON).content(scanPayloadAvecMotif("URGENCE", null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SEANCE_HORS_PLANNING_JOUR"));
    }

    @Test
    void a_closed_day_is_refused_to_the_nurse_but_can_be_forced_by_an_administrator() throws Exception {
        cleanup();
        seedBillablePatientWithoutGenerateur();
        UUID fermeture = UUID.randomUUID();
        jdbc.update("INSERT INTO center_holiday (id, center_id, day_date, label) VALUES (?, ?, CURRENT_DATE, ?)",
                fermeture, CENTER_ID, "Fermeture test");
        try {
            mockMvc.perform(post("/api/v1/seances/scan").with(user("infirmer-01").roles("INFIRMIER"))
                            .contentType(MediaType.APPLICATION_JSON).content(scanPayloadAvecMotif("URGENCE", null)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("SEANCE_CENTRE_FERME"));

            mockMvc.perform(post("/api/v1/seances/scan").with(user("admin-01").roles("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content(scanPayloadAvecMotif("URGENCE", null)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.horsPlanning").value(true));
        } finally {
            jdbc.update("DELETE FROM center_holiday WHERE id = ?", fermeture);
        }
    }
}

