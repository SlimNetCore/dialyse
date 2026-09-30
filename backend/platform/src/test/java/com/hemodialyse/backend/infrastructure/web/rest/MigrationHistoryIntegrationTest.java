package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reprise de l'historique patient et des soldes d'ouverture, écrite dans les vraies tables (H2), puis annulation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class MigrationHistoryIntegrationTest {

    private static final UUID CENTRE = UUID.fromString("99997000-0000-0000-0000-0000000000a1");
    private static final String BASE = "/api/v1/admin/migration";
    private static final List<String> TABLES = List.of("attestation_droit", "prise_en_charge", "dossier_medical_patient",
            "antecedents_medicaux", "serologies_patient", "abords_vasculaires", "resultats_analyses", "seances");

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JdbcTemplate jdbc;
    private MockMvc mockMvc;
    private String batchId;

    private static RequestPostProcessor admin() {
        return user(UserPrincipal.create(UUID.randomUUID().toString(), CENTRE.toString(), "zt-mig-hist", "", List.of("ADMIN"), true));
    }

    private static MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "import.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    @BeforeEach
    void setup() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanup();
        jdbc.update("INSERT INTO forfait (id, center_id, code, libelle, prix) VALUES (?, ?, 'HD-CONV', 'Hémodialyse', 5600)",
                UUID.randomUUID(), CENTRE);
        String body = mockMvc.perform(post(BASE + "/batches").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"libelle\":\"Reprise historique\",\"dateDebutReprise\":\"2023-09-30\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        batchId = body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
        importOk("patients", "Identifiant d'origine;N° d'assurance;Nom;Prénom;Sexe;Date de naissance;Date d'admission\n"
                + "P1;ZH111;BENALI;Karim;M;15/03/1962;02/01/2019\n");
    }

    @AfterEach
    void cleanup() {
        for (String table : List.of("facture_reglements", "facture_lignes", "factures", "attestation_droit", "prise_en_charge",
                "dossier_medical_patient", "antecedents_medicaux", "serologies_patient", "abords_vasculaires",
                "resultats_analyses", "seances", "assure_patient", "patients", "forfait", "migration_entity_run",
                "migration_id_map", "migration_value_map", "migration_batch")) {
            jdbc.update("DELETE FROM " + table + " WHERE center_id = ?", CENTRE);
        }
    }

    @Test
    void importsTheWholeHistoryThenCancelRemovesIt() throws Exception {
        importOk("attestations", "Patient;Date de début;Date de fin\nP1;01/01/2026;31/12/2026\n");
        importOk("prises-en-charge", "Patient;Début demandé;Fin demandée;Forfait demandé (code);Statut\n"
                + "P1;01/01/2026;30/06/2026;HD-CONV;Accordée\n");
        importOk("dossiers-medicaux", "Patient;Date de mise en dialyse;Néphropathie initiale;Statut hépatite B\n"
                + "P1;15/06/2018;Néphropathie diabétique;NEGATIF\n");
        importOk("antecedents", "Patient;Type;Libellé;Code CIM-10;Date de début\nP1;Comorbidité;Diabète de type 2;E11;01/01/2010\n");
        importOk("serologies", "Patient;Marqueur;Résultat;Date de prélèvement\nP1;AgHBs;Négatif;15/01/2026\n");
        importOk("abords-vasculaires", "Patient;Type d'abord;Côté;Date de création\nP1;Fistule;G;10/03/2018\n");
        importOk("analyses", "Patient;Date de prélèvement;Hb;Kt/V;Plaquettes\nP1;05/01/2026;10,8;1,3;210000\n");
        importOk("seances", "Patient;Date de séance;Facturée\nP1;05/01/2026;oui\nP1;07/01/2026;non\n");
        importOk("soldes-ouverture", "N° de facture d'origine;Patient;Date de facture;Montant TTC;Montant déjà réglé\n"
                + "F2026-01;P1;31/01/2026;67200;20000\nF2026-02;P1;28/02/2026;5600;5600\n");

        for (String table : TABLES) {
            assertThat(count(table)).as(table).isPositive();
        }
        assertThat(jdbc.queryForObject("SELECT statut FROM prise_en_charge WHERE center_id = ?", String.class, CENTRE))
                .isEqualTo("VALIDEE");
        assertThat(jdbc.queryForList("SELECT statut FROM seances WHERE center_id = ? ORDER BY date_seance", String.class, CENTRE))
                .containsExactly("FACTUREE", "SIGNEE");
        assertThat(jdbc.queryForObject("SELECT diagnostic_code FROM antecedents_medicaux WHERE center_id = ?", String.class, CENTRE))
                .isEqualTo("E11");
        assertThat(jdbc.queryForObject("SELECT type_abord FROM abords_vasculaires WHERE center_id = ?", String.class, CENTRE))
                .isEqualTo("FAV");
        // Solde d'ouverture : une seule facture (la facture soldée est ignorée), reste à payer 47 200.
        assertThat(jdbc.queryForList("SELECT numero_facture FROM factures WHERE center_id = ?", String.class, CENTRE))
                .containsExactly("REPRISE-F2026-01");
        BigDecimal reste = jdbc.queryForObject("SELECT f.total_ttc - COALESCE((SELECT SUM(r.montant) FROM facture_reglements r "
                + "WHERE r.facture_id = f.id), 0) FROM factures f WHERE f.center_id = ?", BigDecimal.class, CENTRE);
        assertThat(reste).isEqualByComparingTo("47200");

        // Rejeu : aucun doublon.
        importOk("seances", "Patient;Date de séance;Facturée\nP1;05/01/2026;oui\n");
        assertThat(count("seances")).isEqualTo(2);

        mockMvc.perform(post(BASE + "/batches/{id}/cancel", batchId).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANNULE"));
        for (String table : TABLES) {
            assertThat(count(table)).as(table).isZero();
        }
        assertThat(count("factures")).isZero();
        assertThat(count("facture_reglements")).isZero();
        assertThat(count("patients")).isZero();
    }

    @Test
    void cancelIsRefusedOnceAMigratedSessionIsBilledInThePlatform() throws Exception {
        importOk("seances", "Patient;Date de séance;Facturée\nP1;07/01/2026;non\n");
        jdbc.update("UPDATE seances SET facture_id = ?, statut = 'FACTUREE' WHERE center_id = ?", UUID.randomUUID(), CENTRE);

        mockMvc.perform(post(BASE + "/batches/{id}/cancel", batchId).with(admin()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MIGRATION_CANCEL_BLOCKED"));
        assertThat(count("seances")).isEqualTo(1);
        assertThat(count("patients")).isEqualTo(1);
    }

    @Test
    void historyNeedsTheMigratedPatient() throws Exception {
        mockMvc.perform(multipart(BASE + "/batches/{id}/import/attestations", batchId)
                        .file(csv("Patient;Date de début;Date de fin\nINCONNU;01/01/2026;31/12/2026\n"))
                        .param("dryRun", "false").with(admin()))
                .andExpect(jsonPath("$.applied").value(false))
                .andExpect(jsonPath("$.errors[0].code").value("PATIENT_NOT_MIGRATED"));
    }

    private void importOk(String entity, String content) throws Exception {
        mockMvc.perform(multipart(BASE + "/batches/{id}/import/{entity}", batchId, entity).file(csv(content))
                        .param("dryRun", "false").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors.length()").value(0))
                .andExpect(jsonPath("$.applied").value(true));
    }

    private long count(String table) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE center_id = ?", Long.class, CENTRE);
        return n == null ? 0 : n;
    }
}

