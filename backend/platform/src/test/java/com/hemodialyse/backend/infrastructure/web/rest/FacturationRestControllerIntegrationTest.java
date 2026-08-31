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

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class FacturationRestControllerIntegrationTest {

    private static final UUID CENTER_ID = UUID.fromString("99991000-0000-0000-0000-000000000001");
    private static final UUID OTHER_CENTER_ID = UUID.fromString("99991000-0000-0000-0000-000000000002");
    private static final UUID PATIENT_ID = UUID.fromString("21000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_PATIENT_ID = UUID.fromString("21000000-0000-0000-0000-000000000002");
    private static final UUID FORFAIT_ID = UUID.fromString("31000000-0000-0000-0000-000000000001");
    private static final UUID FORFAIT_ALT_ID = UUID.fromString("31000000-0000-0000-0000-000000000009");
    private static final UUID SEANCE_ID = UUID.fromString("41000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_SEANCE_ID = UUID.fromString("41000000-0000-0000-0000-000000000003");
    private static final UUID SEANCE_OTHER_CENTER_ID = UUID.fromString("41000000-0000-0000-0000-000000000002");

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
        seedData();
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM lignes_ecriture WHERE ecriture_id IN (SELECT id FROM ecritures_comptables WHERE center_id IN (?, ?))", CENTER_ID, OTHER_CENTER_ID);
        jdbc.update("DELETE FROM ecritures_comptables WHERE center_id IN (?, ?)", CENTER_ID, OTHER_CENTER_ID);
        jdbc.update("DELETE FROM facturation_settings WHERE center_id IN (?, ?)", CENTER_ID, OTHER_CENTER_ID);
        jdbc.update("DELETE FROM facture_sequence WHERE center_id IN (?, ?)", CENTER_ID, OTHER_CENTER_ID);
        jdbc.update("DELETE FROM facture_lignes WHERE center_id IN (?, ?)", CENTER_ID, OTHER_CENTER_ID);
        jdbc.update("DELETE FROM factures WHERE center_id IN (?, ?)", CENTER_ID, OTHER_CENTER_ID);
        jdbc.update("DELETE FROM seances WHERE id IN (?, ?, ?)", SEANCE_ID, SECOND_SEANCE_ID, SEANCE_OTHER_CENTER_ID);
        jdbc.update("DELETE FROM prise_en_charge WHERE patient_id IN (?, ?)", PATIENT_ID, SECOND_PATIENT_ID);
        jdbc.update("DELETE FROM forfait WHERE id = ?", FORFAIT_ID);
        jdbc.update("DELETE FROM forfait WHERE id = ?", FORFAIT_ALT_ID);
        jdbc.update("DELETE FROM patients WHERE id IN (?, ?)", PATIENT_ID, SECOND_PATIENT_ID);
    }

    @Test
    void preview_should_only_return_current_center_data() throws Exception {
        String payload = """
                {
                  "centerId": "%s",
                  "month": "2026-08",
                  "regroupementMultiForfait": true
                }
                """.formatted(CENTER_ID);

        mockMvc.perform(post("/api/v1/facturation/preview")
                        .contentType("application/json")
                        .content(payload)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.centerId").value(CENTER_ID.toString()))
                .andExpect(jsonPath("$.totalFactures").value(1))
                .andExpect(jsonPath("$.invoices[0].numeroFacture").isNotEmpty())
                .andExpect(jsonPath("$.invoices[0].lines[0].seanceCount").value(1));
    }

    @Test
    void preview_should_keep_forfait_when_pec_end_date_is_open() throws Exception {
        jdbc.update(
                "UPDATE prise_en_charge SET date_fin_effectif = NULL, date_fin_demande = NULL WHERE patient_id = ? AND center_id = ?",
                PATIENT_ID,
                CENTER_ID
        );

        String payload = """
                {
                  "centerId": "%s",
                  "month": "2026-08",
                  "regroupementMultiForfait": true
                }
                """.formatted(CENTER_ID);

        mockMvc.perform(post("/api/v1/facturation/preview")
                        .contentType("application/json")
                        .content(payload)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoices[0].lines[0].forfaitLabel").value("Forfait HD"));
    }

    @Test
    void validate_should_not_double_bill_same_seance() throws Exception {
        String payload = """
                {
                  "centerId": "%s",
                  "userId": "admin",
                  "month": "2026-08",
                  "regroupementMultiForfait": true,
                  "previewGeneratedAt": "2026-08-03T00:00:00Z"
                }
                """.formatted(CENTER_ID);

        mockMvc.perform(post("/api/v1/facturation/validate")
                        .contentType("application/json")
                        .content(payload)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdInvoices").value(1))
                .andExpect(jsonPath("$.billedSeances").value(1));

        mockMvc.perform(post("/api/v1/facturation/validate")
                        .contentType("application/json")
                        .content(payload)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "createdInvoices": 0,
                          "billedSeances": 0
                        }
                        """));

        Integer facturesCount = jdbc.queryForObject(
                "SELECT COUNT(1) FROM factures WHERE center_id = ?",
                Integer.class,
                CENTER_ID
        );
        assertEquals(1, facturesCount == null ? 0 : facturesCount);
    }

    @Test
    void validate_should_generate_distinct_invoice_numbers_for_legacy_code_format() throws Exception {
        seedBillablePatient(
                SECOND_PATIENT_ID,
                "PAT-FAC-002",
                "Merabet",
                "Sonia",
                UUID.fromString("51000000-0000-0000-0000-000000000002")
        );
        seedSeance(SECOND_SEANCE_ID, SECOND_PATIENT_ID, CENTER_ID);
        jdbc.update(
                """
                        MERGE INTO facturation_settings (center_id, tva_rate, code_format, regroupement_multi_forfait, updated_at, updated_by)
                        KEY (center_id)
                        VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, ?)
                        """,
                CENTER_ID,
                new BigDecimal("19.00"),
                "FACT-{YYYY}-{SEQ6}",
                true,
                "test"
        );

        String payload = """
                {
                  "centerId": "%s",
                  "userId": "admin",
                  "month": "2026-08",
                  "regroupementMultiForfait": true,
                  "previewGeneratedAt": "2026-08-03T00:00:00Z"
                }
                """.formatted(CENTER_ID);

        mockMvc.perform(post("/api/v1/facturation/validate")
                        .contentType("application/json")
                        .content(payload)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdInvoices").value(2))
                .andExpect(jsonPath("$.billedSeances").value(2));

        assertEquals(
                2,
                jdbc.queryForObject(
                        "SELECT COUNT(DISTINCT numero_facture) FROM factures WHERE center_id = ? AND numero_facture LIKE 'FACT-2026-%'",
                        Integer.class,
                        CENTER_ID
                )
        );
    }

    @Test
    void validate_should_generate_and_expose_comptabilite_ecriture_for_same_center_and_period() throws Exception {
        String payload = """
                {
                  "centerId": "%s",
                  "userId": "admin",
                  "month": "2026-08",
                  "regroupementMultiForfait": true,
                  "previewGeneratedAt": "2026-08-03T00:00:00Z"
                }
                """.formatted(CENTER_ID);

        mockMvc.perform(post("/api/v1/facturation/validate")
                        .contentType("application/json")
                        .content(payload)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdInvoices").value(1))
                .andExpect(jsonPath("$.billedSeances").value(1));

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(1) FROM factures WHERE center_id = ?",
                Integer.class,
                CENTER_ID
        ));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(1) FROM ecritures_comptables WHERE center_id = ?",
                Integer.class,
                CENTER_ID
        ));

        mockMvc.perform(get("/api/v1/comptabilite/ecritures")
                        .param("centerId", CENTER_ID.toString())
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31")
                        .param("page", "0")
                        .param("size", "20")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].centerId").value(CENTER_ID.toString()))
                .andExpect(jsonPath("$.items[0].journalCode").value("VE"))
                .andExpect(jsonPath("$.items[0].statut").value("VALIDEE"));
    }

    @Test
    void preview_exclude_seance_should_mark_seance_absent_and_recalculate() throws Exception {
        String payload = """
                {
                  "centerId": "%s",
                  "userId": "admin",
                  "month": "2026-08",
                  "regroupementMultiForfait": true
                }
                """.formatted(CENTER_ID);

        mockMvc.perform(post("/api/v1/facturation/preview/seances/{seanceId}/exclude", SEANCE_ID)
                        .contentType("application/json")
                        .content(payload)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFactures").value(0));

        String statut = jdbc.queryForObject(
                "SELECT statut FROM seances WHERE id = ? AND center_id = ?",
                String.class,
                SEANCE_ID,
                CENTER_ID
        );
        assertEquals("ABSENT", statut);
    }

    @Test
    void preview_update_forfait_should_recalculate_invoice_line_amount() throws Exception {
        jdbc.update(
                "MERGE INTO forfait (id, center_id, code, libelle, prix) KEY (id) VALUES (?, ?, ?, ?, ?)",
                FORFAIT_ALT_ID,
                CENTER_ID,
                "F-HD-ALT",
                "Forfait HD Premium",
                new BigDecimal("5200.00")
        );

        String payload = """
                {
                  "centerId": "%s",
                  "userId": "admin",
                  "forfaitId": "%s",
                  "month": "2026-08",
                  "regroupementMultiForfait": true
                }
                """.formatted(CENTER_ID, FORFAIT_ALT_ID);

        mockMvc.perform(post("/api/v1/facturation/preview/seances/{seanceId}/forfait", SEANCE_ID)
                        .contentType("application/json")
                        .content(payload)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoices[0].lines[0].forfaitId").value(FORFAIT_ALT_ID.toString()))
                .andExpect(jsonPath("$.invoices[0].lines[0].lineHt").value(5200.00));
    }

    private void seedData() {
        seedBillablePatient(
                PATIENT_ID,
                "PAT-FAC-001",
                "Benaissa",
                "Ines",
                UUID.fromString("51000000-0000-0000-0000-000000000001")
        );
        seedSeance(SEANCE_ID, PATIENT_ID, CENTER_ID);
        seedSeance(SEANCE_OTHER_CENTER_ID, PATIENT_ID, OTHER_CENTER_ID);
    }

    private void seedBillablePatient(UUID patientId,
                                     String patientCode,
                                     String nom,
                                     String prenom,
                                     UUID priseEnChargeId) {
        jdbc.update(
                """
                INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, type_patient, etat_patient, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                patientId,
                CENTER_ID,
                patientCode,
                nom,
                prenom,
                "F",
                Date.valueOf(LocalDate.of(2026, 1, 10)),
                "ASS-FA-1001",
                "NON_VACANCIER",
                "PERMANENT",
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        jdbc.update(
                "MERGE INTO forfait (id, center_id, code, libelle, prix) KEY (id) VALUES (?, ?, ?, ?, ?)",
                FORFAIT_ID,
                CENTER_ID,
                "F-HD",
                "Forfait HD",
                new BigDecimal("3500.00")
        );

        jdbc.update(
                "MERGE INTO forfait (id, center_id, code, libelle, prix) KEY (id) VALUES (?, ?, ?, ?, ?)",
                FORFAIT_ALT_ID,
                CENTER_ID,
                "F-HD-ALT",
                "Forfait HD Premium",
                new BigDecimal("5200.00")
        );

        jdbc.update(
                """
                INSERT INTO prise_en_charge (
                  id, center_id, patient_id, date_debut_demande, date_fin_demande,
                  date_debut_effectif, date_fin_effectif, forfait_demande_id, forfait_effectif_id, statut, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                priseEnChargeId,
                CENTER_ID,
                patientId,
                Date.valueOf(LocalDate.of(2026, 1, 1)),
                Date.valueOf(LocalDate.of(2026, 12, 31)),
                Date.valueOf(LocalDate.of(2026, 1, 1)),
                Date.valueOf(LocalDate.of(2026, 12, 31)),
                FORFAIT_ID,
                FORFAIT_ID,
                "VALIDEE",
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    private void seedSeance(UUID seanceId, UUID patientId, UUID centerId) {
        jdbc.update(
                """
                INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at, validated_at, signed_infirmier_at, signed_medecin_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                seanceId,
                patientId,
                centerId,
                Date.valueOf(LocalDate.of(2026, 8, 2)),
                "SIGNEE",
                OffsetDateTime.now(ZoneOffset.UTC),
                OffsetDateTime.now(ZoneOffset.UTC),
                OffsetDateTime.now(ZoneOffset.UTC),
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }
}

