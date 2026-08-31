package com.hemodialyse.backend.infrastructure.web.rest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class DocumentRestControllerSyntheseMensuelleIntegrationTest {

    private static final UUID CENTER_ID = UUID.fromString("99992000-0000-0000-0000-000000000001");
    private static final UUID OTHER_CENTER_ID = UUID.fromString("99992000-0000-0000-0000-000000000002");
    private static final UUID MODELE_ID = UUID.fromString("99992000-0000-0000-0000-000000000010");
    private static final UUID CAISSE_ID = UUID.fromString("99992000-0000-0000-0000-000000000011");
    private static final UUID AGENCE_ID = UUID.fromString("99992000-0000-0000-0000-000000000012");
    private static final UUID FACTURE_ID = UUID.fromString("99992000-0000-0000-0000-000000000013");
    private static final UUID LIGNE_ID = UUID.fromString("99992000-0000-0000-0000-000000000014");
    private static final UUID OTHER_FACTURE_ID = UUID.fromString("99992000-0000-0000-0000-000000000015");
    private static final UUID OTHER_LIGNE_ID = UUID.fromString("99992000-0000-0000-0000-000000000016");

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
    void afterEach() {
        cleanup();
    }

    @Test
    void print_should_generate_pdf_for_monthly_billing_summary() throws Exception {
        String payload = """
                {
                  "centerId": "%s",
                  "typeDocument": "SYNTHESE_FACTURATION_MENSUELLE",
                  "formatOverride": "PDF",
                  "params": {
                    "PERIOD_START": "2026-08-01",
                    "PERIOD_END": "2026-08-31"
                  }
                }
                """.formatted(CENTER_ID);

        mockMvc.perform(post("/api/v1/documents/print")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString(MediaType.APPLICATION_PDF_VALUE)))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("synthese-facturation-mensuelle.pdf")));
    }

    @Test
    void print_should_reject_monthly_summary_without_required_period() throws Exception {
        String payload = """
                {
                  "centerId": "%s",
                  "typeDocument": "SYNTHESE_FACTURATION_MENSUELLE",
                  "formatOverride": "PDF",
                  "params": {
                    "PERIOD_START": "2026-08-01"
                  }
                }
                """.formatted(CENTER_ID);

        mockMvc.perform(post("/api/v1/documents/print")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    private void seedData() {
        jdbc.update("""
                        INSERT INTO modele_document (id, center_id, code, libelle, type_document, chemin_jrxml, format_impression, description, active, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                MODELE_ID,
                CENTER_ID,
                "SYNTHESE_FACTURATION_MENSUELLE",
                "Synthese mensuelle facturation",
                "SYNTHESE_FACTURATION_MENSUELLE",
                "reports/synthese_mensuelle_facturation.jrxml",
                "PDF",
                "Synthese croisee forfait/caisse",
                true,
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        jdbc.update("INSERT INTO caisse_assurance (id, center_id, code, nom, type_caisse) VALUES (?, ?, ?, ?, ?)",
                CAISSE_ID, CENTER_ID, "CNAS-AN", "CNAS Annaba", "STANDARD");
        jdbc.update("INSERT INTO agence (id, center_id, caisse_id, code, nom) VALUES (?, ?, ?, ?, ?)",
                AGENCE_ID, CENTER_ID, CAISSE_ID, "AN-01", "Agence Annaba 1");

        jdbc.update("""
                        INSERT INTO factures (
                            id, center_id, patient_id, numero_facture, patient_code, patient_full_name, patient_status_snapshot,
                            numero_immatriculation_snapshot, centre_payeur_id_snapshot, agence_id_snapshot,
                            period_start, period_end, date_facturation, tva_rate, total_ht, total_tva, total_ttc, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                FACTURE_ID,
                CENTER_ID,
                UUID.randomUUID(),
                "FAC-2026-000001",
                "PAT-001",
                "Patient Test",
                "PERMANENT",
                "ASS-001",
                null,
                AGENCE_ID,
                Date.valueOf(LocalDate.of(2026, 8, 1)),
                Date.valueOf(LocalDate.of(2026, 8, 31)),
                Date.valueOf(LocalDate.of(2026, 8, 10)),
                new BigDecimal("19.00"),
                new BigDecimal("5000.00"),
                new BigDecimal("950.00"),
                new BigDecimal("5950.00"),
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        jdbc.update("""
                        INSERT INTO facture_lignes (id, facture_id, center_id, forfait_id, forfait_label, unit_price_ht, seance_count, line_ht)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                LIGNE_ID,
                FACTURE_ID,
                CENTER_ID,
                UUID.randomUUID(),
                "Forfait HD",
                new BigDecimal("2500.00"),
                2,
                new BigDecimal("5000.00")
        );

        // Donnees d'un autre centre pour verifier l'isolation center_id au niveau requete Jasper
        jdbc.update("""
                        INSERT INTO factures (
                            id, center_id, patient_id, numero_facture, patient_code, patient_full_name, patient_status_snapshot,
                            numero_immatriculation_snapshot, centre_payeur_id_snapshot, agence_id_snapshot,
                            period_start, period_end, date_facturation, tva_rate, total_ht, total_tva, total_ttc, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                OTHER_FACTURE_ID,
                OTHER_CENTER_ID,
                UUID.randomUUID(),
                "FAC-OTHER-01",
                "PAT-OTHER",
                "Patient Other",
                "PERMANENT",
                "ASS-OTHER",
                null,
                null,
                Date.valueOf(LocalDate.of(2026, 8, 1)),
                Date.valueOf(LocalDate.of(2026, 8, 31)),
                Date.valueOf(LocalDate.of(2026, 8, 15)),
                new BigDecimal("19.00"),
                new BigDecimal("9900.00"),
                new BigDecimal("1881.00"),
                new BigDecimal("11781.00"),
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        jdbc.update("""
                        INSERT INTO facture_lignes (id, facture_id, center_id, forfait_id, forfait_label, unit_price_ht, seance_count, line_ht)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                OTHER_LIGNE_ID,
                OTHER_FACTURE_ID,
                OTHER_CENTER_ID,
                UUID.randomUUID(),
                "Forfait Other",
                new BigDecimal("3300.00"),
                3,
                new BigDecimal("9900.00")
        );
    }

    private void cleanup() {
        jdbc.update("DELETE FROM facture_lignes WHERE id IN (?, ?)", LIGNE_ID, OTHER_LIGNE_ID);
        jdbc.update("DELETE FROM factures WHERE id IN (?, ?)", FACTURE_ID, OTHER_FACTURE_ID);
        jdbc.update("DELETE FROM modele_document WHERE id = ?", MODELE_ID);
        jdbc.update("DELETE FROM agence WHERE id = ?", AGENCE_ID);
        jdbc.update("DELETE FROM caisse_assurance WHERE id = ?", CAISSE_ID);
    }
}

