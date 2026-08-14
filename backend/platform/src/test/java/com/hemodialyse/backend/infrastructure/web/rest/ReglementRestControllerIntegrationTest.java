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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class ReglementRestControllerIntegrationTest {

    private static final UUID CENTER_ID = UUID.fromString("77770000-0000-0000-0000-000000000001");
    private static final UUID OTHER_CENTER_ID = UUID.fromString("77770000-0000-0000-0000-000000000002");
    private static final UUID CAISSE_ID = UUID.fromString("77770000-0000-0000-0000-000000000011");
    private static final UUID AGENCE_ID = UUID.fromString("77770000-0000-0000-0000-000000000012");
    private static final UUID CENTRE_PAYEUR_ID = UUID.fromString("77770000-0000-0000-0000-000000000013");
    private static final UUID OTHER_CAISSE_ID = UUID.fromString("77770000-0000-0000-0000-000000000021");
    private static final UUID OTHER_AGENCE_ID = UUID.fromString("77770000-0000-0000-0000-000000000022");
    private static final UUID OTHER_CENTRE_PAYEUR_ID = UUID.fromString("77770000-0000-0000-0000-000000000023");
    private static final UUID PATIENT_ID = UUID.fromString("77770000-0000-0000-0000-000000000101");
    private static final UUID PATIENT_ID_2 = UUID.fromString("77770000-0000-0000-0000-000000000102");
    private static final UUID OTHER_PATIENT_ID = UUID.fromString("77770000-0000-0000-0000-000000000103");
    private static final UUID FACTURE_ID = UUID.fromString("77770000-0000-0000-0000-000000000201");
    private static final UUID FACTURE_ID_2 = UUID.fromString("77770000-0000-0000-0000-000000000202");
    private static final UUID OTHER_FACTURE_ID = UUID.fromString("77770000-0000-0000-0000-000000000203");

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
        seedCenterData();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void search_should_return_only_current_center_and_compute_statuses() throws Exception {
        seedPayment(FACTURE_ID, CENTER_ID, new BigDecimal("1000.00"), LocalDate.of(2026, 8, 10), "sec-01");
        seedPayment(FACTURE_ID_2, CENTER_ID, new BigDecimal("300.00"), LocalDate.of(2026, 8, 11), "sec-01");
        seedPayment(OTHER_FACTURE_ID, OTHER_CENTER_ID, new BigDecimal("999.00"), LocalDate.of(2026, 8, 9), "other");

        mockMvc.perform(get("/api/v1/reglements")
                        .param("centerId", CENTER_ID.toString())
                        .param("year", "2026")
                        .param("month", "8")
                        .param("page", "0")
                        .param("size", "20")
                        .with(user("secretaire").roles("SECRETAIRE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[?(@.factureId=='%s' && @.etat=='REGLEE' && @.soldeType=='REGLE')]".formatted(FACTURE_ID)).isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.factureId=='%s' && @.etat=='PARTIELLEMENT_REGLEE' && @.reste==700.0)]".formatted(FACTURE_ID_2)).isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.factureId=='%s')]".formatted(OTHER_FACTURE_ID)).isEmpty());
    }

    @Test
    void registerPayment_should_update_status_and_trop_percu() throws Exception {
        mockMvc.perform(post("/api/v1/reglements/{factureId}/paiements", FACTURE_ID_2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "centerId": "%s",
                                  "montant": 1200.00,
                                  "dateReglement": "2026-08-14",
                                  "userId": "sec-02"
                                }
                                """.formatted(CENTER_ID))
                        .with(user("secretaire").roles("SECRETAIRE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.factureId").value(FACTURE_ID_2.toString()))
                .andExpect(jsonPath("$.montantRegle").value(1200.0))
                .andExpect(jsonPath("$.etat").value("REGLEE"))
                .andExpect(jsonPath("$.soldeType").value("TROP_PERCU"))
                .andExpect(jsonPath("$.tropPercu").value(200.0));
    }

    @Test
    void dashboard_should_aggregate_total_paid_and_unpaid_counts() throws Exception {
        seedPayment(FACTURE_ID, CENTER_ID, new BigDecimal("1000.00"), LocalDate.of(2026, 8, 10), "sec-01");
        seedPayment(FACTURE_ID_2, CENTER_ID, new BigDecimal("300.00"), LocalDate.of(2026, 8, 11), "sec-01");

        mockMvc.perform(get("/api/v1/reglements/dashboard")
                        .param("centerId", CENTER_ID.toString())
                        .param("year", "2026")
                        .param("month", "8")
                        .with(user("secretaire").roles("SECRETAIRE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFactures").value(2))
                .andExpect(jsonPath("$.nonReglees").value(0))
                .andExpect(jsonPath("$.partiellementReglees").value(1))
                .andExpect(jsonPath("$.reglees").value(1))
                .andExpect(jsonPath("$.totalFacture").value(2000.0))
                .andExpect(jsonPath("$.totalRegle").value(1300.0))
                .andExpect(jsonPath("$.totalReste").value(700.0));
    }

    private void seedCenterData() {
        seedCaisse(CENTER_ID, CAISSE_ID, "CNAS", "CNAS Centre 1");
        seedAgence(CENTER_ID, AGENCE_ID, CAISSE_ID, "AG-01", "Agence A");
        seedCentrePayeur(CENTER_ID, CENTRE_PAYEUR_ID, AGENCE_ID, "CP-01", "Centre Payeur A");
        seedPatient(PATIENT_ID, CENTER_ID, CENTRE_PAYEUR_ID, "Dialyse", "Alpha", "ASS-001");
        seedPatient(PATIENT_ID_2, CENTER_ID, CENTRE_PAYEUR_ID, "Dialyse", "Beta", "ASS-002");
        seedFacture(FACTURE_ID, CENTER_ID, PATIENT_ID, AGENCE_ID, CENTRE_PAYEUR_ID, "FAC-2026-0001", "Dialyse Alpha", LocalDate.of(2026, 8, 1), new BigDecimal("1000.00"));
        seedFacture(FACTURE_ID_2, CENTER_ID, PATIENT_ID_2, AGENCE_ID, CENTRE_PAYEUR_ID, "FAC-2026-0002", "Dialyse Beta", LocalDate.of(2026, 8, 2), new BigDecimal("1000.00"));

        seedCaisse(OTHER_CENTER_ID, OTHER_CAISSE_ID, "CASNOS", "CASNOS Centre 2");
        seedAgence(OTHER_CENTER_ID, OTHER_AGENCE_ID, OTHER_CAISSE_ID, "AG-02", "Agence B");
        seedCentrePayeur(OTHER_CENTER_ID, OTHER_CENTRE_PAYEUR_ID, OTHER_AGENCE_ID, "CP-02", "Centre Payeur B");
        seedPatient(OTHER_PATIENT_ID, OTHER_CENTER_ID, OTHER_CENTRE_PAYEUR_ID, "Autre", "Patient", "ASS-003");
        seedFacture(OTHER_FACTURE_ID, OTHER_CENTER_ID, OTHER_PATIENT_ID, OTHER_AGENCE_ID, OTHER_CENTRE_PAYEUR_ID, "FAC-2026-9999", "Autre Patient", LocalDate.of(2026, 8, 3), new BigDecimal("900.00"));
    }

    private void seedCaisse(UUID centerId, UUID caisseId, String code, String nom) {
        jdbc.update("INSERT INTO caisse_assurance (id, center_id, code, nom, type_caisse) VALUES (?, ?, ?, ?, 'STANDARD')",
                caisseId, centerId, code, nom);
    }

    private void seedAgence(UUID centerId, UUID agenceId, UUID caisseId, String code, String nom) {
        jdbc.update("INSERT INTO agence (id, center_id, caisse_id, code, nom) VALUES (?, ?, ?, ?, ?)",
                agenceId, centerId, caisseId, code, nom);
    }

    private void seedCentrePayeur(UUID centerId, UUID centrePayeurId, UUID agenceId, String code, String nom) {
        jdbc.update("INSERT INTO centre_payeur (id, center_id, agence_id, code, nom, adresse) VALUES (?, ?, ?, ?, ?, ?)",
                centrePayeurId, centerId, agenceId, code, nom, "Adresse test");
    }

    private void seedPatient(UUID patientId, UUID centerId, UUID centrePayeurId, String nom, String prenom, String numeroAssurance) {
        jdbc.update(
                """
                        INSERT INTO patients (
                            id, center_id, code_patient, nom, prenom, sexe, date_admission,
                            numero_assurance, type_patient, centre_payeur_id, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                        """,
                patientId,
                centerId,
                "PAT-" + patientId.toString().substring(0, 8),
                nom,
                prenom,
                "M",
                LocalDate.of(2026, 1, 1),
                numeroAssurance,
                "NON_VACANCIER",
                centrePayeurId
        );
    }

    private void seedFacture(UUID factureId,
                             UUID centerId,
                             UUID patientId,
                             UUID agenceId,
                             UUID centrePayeurId,
                             String numeroFacture,
                             String patientFullName,
                             LocalDate dateFacturation,
                             BigDecimal totalTtc) {
        jdbc.update(
                """
                        INSERT INTO factures (
                            id, center_id, patient_id, numero_facture, patient_code, patient_full_name,
                            patient_status_snapshot, numero_immatriculation_snapshot, centre_payeur_id_snapshot,
                            agence_id_snapshot, period_start, period_end, date_facturation, tva_rate,
                            total_ht, total_tva, total_ttc, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                        """,
                factureId,
                centerId,
                patientId,
                numeroFacture,
                "PAT-SNAPSHOT",
                patientFullName,
                "PERMANENT",
                "ASS-SNAPSHOT",
                centrePayeurId,
                agenceId,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                dateFacturation,
                new BigDecimal("19.00"),
                totalTtc,
                BigDecimal.ZERO,
                totalTtc
        );
    }

    private void seedPayment(UUID factureId, UUID centerId, BigDecimal montant, LocalDate dateReglement, String userId) {
        jdbc.update(
                "INSERT INTO facture_reglements (id, facture_id, center_id, montant, date_reglement, saisi_par, created_at) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                UUID.randomUUID(), factureId, centerId, montant, dateReglement, userId
        );
    }

    private void cleanup() {
        jdbc.update("DELETE FROM facture_reglements WHERE facture_id IN (?, ?, ?)", FACTURE_ID, FACTURE_ID_2, OTHER_FACTURE_ID);
        jdbc.update("DELETE FROM factures WHERE id IN (?, ?, ?)", FACTURE_ID, FACTURE_ID_2, OTHER_FACTURE_ID);
        jdbc.update("DELETE FROM patients WHERE id IN (?, ?, ?)", PATIENT_ID, PATIENT_ID_2, OTHER_PATIENT_ID);
        jdbc.update("DELETE FROM centre_payeur WHERE id IN (?, ?)", CENTRE_PAYEUR_ID, OTHER_CENTRE_PAYEUR_ID);
        jdbc.update("DELETE FROM agence WHERE id IN (?, ?)", AGENCE_ID, OTHER_AGENCE_ID);
        jdbc.update("DELETE FROM caisse_assurance WHERE id IN (?, ?)", CAISSE_ID, OTHER_CAISSE_ID);
    }
}


