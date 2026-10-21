package com.hemodialyse.backend.characterization.facturation.pilotage;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * F5 : les lectures de pilotage voient les écritures produites par le nouveau module (F3 : {@code POST /facturation/validate},
 * F4 : {@code POST /reglements/{facture}/paiements}) sur les mêmes tables. Le jour de facturation (jour de validation, donc
 * variable) est ramené par SQL à une date fixe de 2026 avant de lire : la fixture est ainsi indépendante du jour d'exécution.
 */
class CoherenceAvecLesEcrituresDuNouveauModuleCharacterizationIntegrationTest extends PilotageFacturationSupport {

    private static final UUID FORFAIT_HD = nom("FORFAIT_HD");
    private static final UUID FORFAIT_PREMIUM = nom("FORFAIT_PREMIUM");

    @Override
    protected String theme() {
        return "coherence-nouveau-module";
    }

    private void referentielDeFacturation() {
        jdbc.update("INSERT INTO forfait (id, center_id, code, libelle, prix) VALUES (?, ?, 'F-HD', 'Forfait HD', 33.33)", FORFAIT_HD, CENTRE_A);
        jdbc.update("INSERT INTO forfait (id, center_id, code, libelle, prix) VALUES (?, ?, 'F-HDP', 'Forfait HD Premium', 12.50)", FORFAIT_PREMIUM, CENTRE_A);
        pec(P_AMRANI, FORFAIT_HD);
        pec(P_BELKACEM, FORFAIT_HD);
        pec(P_CHIKHI, FORFAIT_PREMIUM);
        jdbc.update("DELETE FROM facturation_settings WHERE center_id = ?", CENTRE_A);
        jdbc.update("INSERT INTO facturation_settings (center_id, code_format, regroupement_multi_forfait, updated_at, updated_by) "
                + "VALUES (?, 'FAC-{YYYY}-{SEQ}', TRUE, CURRENT_TIMESTAMP, 'fixture')", CENTRE_A);
        jdbc.update("DELETE FROM facture_sequence WHERE center_id = ?", CENTRE_A);
        jdbc.update("INSERT INTO facture_sequence (center_id, seq_year, seq_value) VALUES (?, ?, 41)", CENTRE_A, LocalDate.now().getYear());
        jdbc.update("DELETE FROM tva_types WHERE center_id = ?", CENTRE_A);
        jdbc.update("INSERT INTO tva_types (id, center_id, libelle, taux, type_prestation, exonere, date_debut_validite, "
                + "date_fin_validite, actif, created_by) VALUES (?, ?, 'TVA', 19.00, 'HEMODIALYSE', FALSE, ?, NULL, TRUE, 'fixture')",
                UUID.randomUUID(), CENTRE_A, Date.valueOf(LocalDate.of(2000, 1, 1)));
        // l'agence d'une facture vient du centre payeur du patient : Amrani -> agence 1 (CNAS), les autres sans payeur
        UUID payeur = nom("PAYEUR_1");
        jdbc.update("INSERT INTO centre_payeur (id, center_id, agence_id, code, nom) VALUES (?, ?, ?, 'CP-01', 'Centre payeur 1')",
                payeur, CENTRE_A, AGENCE_1);
        jdbc.update("UPDATE patients SET centre_payeur_id = ? WHERE id = ?", payeur, P_AMRANI);
    }

    private void pec(UUID patient, UUID forfait) {
        jdbc.update("INSERT INTO prise_en_charge (id, center_id, patient_id, date_debut_demande, date_fin_demande, "
                        + "date_debut_effectif, date_fin_effectif, forfait_demande_id, forfait_effectif_id, statut, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'VALIDEE', ?)",
                UUID.randomUUID(), CENTRE_A, patient, Date.valueOf("2025-01-01"), Date.valueOf("2027-12-31"),
                Date.valueOf("2025-01-01"), Date.valueOf("2027-12-31"), forfait, forfait,
                OffsetDateTime.of(2025, 1, 1, 8, 0, 0, 0, ZoneOffset.UTC));
    }

    private void validerJanvier() throws Exception {
        referentielDeFacturation();
        seance(P_AMRANI, CENTRE_A, "2026-01-05", "VALIDEE", null);
        seance(P_AMRANI, CENTRE_A, "2026-01-12", "VALIDEE", null);
        seance(P_AMRANI, CENTRE_A, "2026-01-19", "SIGNEE", null);
        seance(P_BELKACEM, CENTRE_A, "2026-01-06", "VALIDEE", null);
        seance(P_CHIKHI, CENTRE_A, "2026-01-07", "VALIDEE", null);
        Resp r = doPost(FACTURATION + "/validate", json("{'centerId':'" + CENTRE_A + "','userId':'u1','month':'2026-01',"
                + "'regroupementMultiForfait':true}"), adminA());
        assertEquals(200, r.status(), r.raw());
        jdbc.update("UPDATE factures SET date_facturation = ? WHERE center_id = ?", Date.valueOf("2026-01-31"), CENTRE_A);
    }

    private UUID factureDe(UUID patient) {
        return jdbc.queryForObject("SELECT id FROM factures WHERE patient_id = ?", UUID.class, patient);
    }

    @Test
    void le_tableau_de_bord_et_la_synthese_voient_les_factures_emises_par_la_validation() throws Exception {
        validerJanvier();

        golden("dashboard_apres_validation", dashboard(CENTRE_A, "2026-01", adminA()));
        goldenPdf("synthese_apres_validation_nouveau_code", synthese(CENTRE_A, "2026-01-01", "2026-01-31", "PDF", adminA()));
    }

    @Test
    void la_vue_d_ensemble_de_la_direction_voit_les_factures_emises_puis_les_reglements_enregistres() throws Exception {
        validerJanvier();
        JsonNode avant = json(doGet(DIRECTION + "/overview?from=2026-01-01&to=2026-01-31", direction()));
        UUID facture = factureDe(P_AMRANI);
        BigDecimal ttcAmrani = jdbc.queryForObject("SELECT total_ttc FROM factures WHERE id = ?", BigDecimal.class, facture);

        Resp reglement = doPost("/api/v1/reglements/" + facture + "/paiements",
                json("{'centerId':'" + CENTRE_A + "','montant':50.00,'dateReglement':'2026-02-10','userId':'caissier'}"), adminA());
        assertEquals(200, reglement.status(), reglement.raw());
        JsonNode apres = json(doGet(DIRECTION + "/overview?from=2026-01-01&to=2026-01-31", direction()));
        JsonNode dashboardApres = json(dashboard(CENTRE_A, "2026-01", adminA()));

        ObjectNode vue = mapper.createObjectNode();
        vue.set("avantReglement", projeter(avant.path("totaux"), "factures", "caHt", "caTtc", "encaisse", "resteARecouvrer", "tauxEncaissement"));
        vue.set("apresReglement", projeter(apres.path("totaux"), "factures", "caHt", "caTtc", "encaisse", "resteARecouvrer", "tauxEncaissement"));
        vue.put("ttcFactureAmrani", ttcAmrani.toPlainString());
        vue.set("tableauDeBordApresReglement", projeter(dashboardApres, "revenueTtc", "revenueHt", "billedSeances", "createdInvoices"));
        goldenNode("direction_avant_apres_reglement", vue);
    }

    @Test
    void les_seances_validees_passent_a_facturee_et_comptent_dans_les_seances_facturees_du_tableau_de_bord() throws Exception {
        validerJanvier();
        Long facturees = jdbc.queryForObject("SELECT COUNT(*) FROM seances WHERE center_id = ? AND statut = 'FACTUREE'", Long.class, CENTRE_A);

        JsonNode d = json(dashboard(CENTRE_A, "2026-01", adminA()));

        assertEquals(5L, facturees);
        assertEquals(5, d.path("billedSeances").asInt());
        assertEquals(3, d.path("createdInvoices").asInt());
    }
}
