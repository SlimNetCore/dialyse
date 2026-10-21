package com.hemodialyse.backend.characterization.facturation.retrait;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Slice facturation-f6-retrait-bc-facturation, consommateur Migration : la reprise des soldes d'ouverture écrit dans les
 * tables de la Facturation ({@code factures}, {@code facture_lignes}, {@code facture_reglements}) par
 * {@code OpeningBalanceJdbcAdapter} et les supprime à l'annulation du lot ({@code MigrationRollbackJdbcAdapter}). Point
 * d'observation : les routes admin de migration ({@code /api/v1/admin/migration}), puis les lectures des autres consommateurs
 * (Règlement F4, Comptabilité C5). Déjà verrouillé ailleurs ({@code MigrationHistoryIntegrationTest}, sans golden) : import
 * nominal avec facture soldée ignorée, rejeu sans doublon, annulation, refus d'annulation d'une séance facturée.
 */
class FacturesDeRepriseCharacterizationIntegrationTest extends RetraitCharacterizationSupport {

    private static final String BASE = "/api/v1/admin/migration";
    private static final String ENTETE = "N° de facture d'origine;Patient;Date de facture;Montant TTC;Montant déjà réglé\n";
    private static final String SOLDES = ENTETE
            + "F2026-01;P1;31/01/2026;67200;20000\n"
            + "F2026-02;P1;28/02/2026;5600;5600\n"
            + "F2026-03;P1;15/03/2026;12000;0\n";
    private String lot;

    @Override
    protected String theme() {
        return "reprise";
    }

    @BeforeEach
    void lotDeReprise() throws Exception {
        Resp r = appeler(post(BASE + "/batches"), adminA(), json("{'libelle':'Reprise historique','dateDebutReprise':'2023-09-30'}"));
        assertThat(r.status()).isEqualTo(201);
        lot = json(r).path("id").asString();
        nommer(UUID.fromString(lot), "<LOT_REPRISE>");
        importer("patients", "Identifiant d'origine;N° d'assurance;Nom;Prénom;Sexe;Date de naissance;Date d'admission\n"
                + "P1;ZH111;BENALI;Karim;M;15/03/1962;02/01/2019\n");
    }

    private Resp importer(String entite, String contenu) throws Exception {
        return executer(multipart(BASE + "/batches/{id}/import/{entity}", lot, entite)
                .file(new MockMultipartFile("file", "import.csv", "text/csv", contenu.getBytes(StandardCharsets.UTF_8)))
                .param("dryRun", "false").with(adminA()));
    }

    private Resp annuler() throws Exception {
        return appeler(post(BASE + "/batches/{id}/cancel", lot), adminA());
    }

    /**
     * Nomme les identifiants générés (factures par numéro, règlements par numéro de facture) pour les goldens.
     */
    private void nommerLesIdentifiants() {
        int i = 0;
        for (Map<String, Object> f : jdbc.queryForList("SELECT id, numero_facture FROM factures WHERE center_id = ? ORDER BY numero_facture",
                CENTRE_A)) {
            nommer((UUID) f.get("ID"), "<FACTURE_" + f.get("NUMERO_FACTURE") + ">");
            i++;
        }
        List<Map<String, Object>> reglements = jdbc.queryForList("SELECT r.id, f.numero_facture FROM facture_reglements r "
                + "JOIN factures f ON f.id = r.facture_id WHERE r.center_id = ? ORDER BY f.numero_facture, r.date_reglement, r.montant",
                CENTRE_A);
        for (Map<String, Object> r : reglements) {
            nommer((UUID) r.get("ID"), "<REGLEMENT_" + r.get("NUMERO_FACTURE") + "_" + (i++) + ">");
        }
    }

    private ObjectNode etat() {
        nommerLesIdentifiants();
        ObjectNode o = mapper.createObjectNode();
        o.set("factures", lignes("SELECT numero_facture, patient_code, patient_full_name, patient_status_snapshot, "
                + "numero_immatriculation_snapshot, centre_payeur_id_snapshot, agence_id_snapshot, period_start, period_end, "
                + "date_facturation, tva_rate, total_ht, total_tva, total_ttc FROM factures WHERE center_id = ? "
                + "ORDER BY numero_facture", CENTRE_A));
        o.set("lignes", lignes("SELECT f.numero_facture, l.forfait_id, l.forfait_label, l.unit_price_ht, l.seance_count, l.line_ht "
                + "FROM facture_lignes l JOIN factures f ON f.id = l.facture_id WHERE l.center_id = ? ORDER BY f.numero_facture",
                CENTRE_A));
        o.set("reglements", lignes("SELECT f.numero_facture, r.montant, r.date_reglement, r.saisi_par, r.code_reglement, "
                + "r.mode_reglement FROM facture_reglements r JOIN factures f ON f.id = r.facture_id WHERE r.center_id = ? "
                + "ORDER BY f.numero_facture, r.date_reglement, r.montant", CENTRE_A));
        o.put("sequences", jdbc.queryForObject("SELECT COUNT(*) FROM facture_sequence WHERE center_id = ?", Integer.class, CENTRE_A));
        return o;
    }

    @Test
    void les_soldes_d_ouverture_ecrivent_une_facture_une_ligne_et_un_reglement_de_reprise_par_solde_non_nul() throws Exception {
        Resp importe = importer("soldes-ouverture", SOLDES);
        golden("import_reponse", importe);
        goldenNode("import_etat", etat());
    }

    @Test
    void la_facture_de_reprise_fige_le_payeur_du_patient_et_son_agence() throws Exception {
        jdbc.update("INSERT INTO caisse_assurance (id, center_id, code, nom, type_caisse) VALUES (?, ?, 'CNAS', 'CNAS', 'STANDARD')",
                CAISSE_1, CENTRE_A);
        jdbc.update("INSERT INTO agence (id, center_id, caisse_id, code, nom) VALUES (?, ?, ?, 'AG-1', 'Agence 1')",
                AGENCE_1, CENTRE_A, CAISSE_1);
        jdbc.update("INSERT INTO centre_payeur (id, center_id, agence_id, code, nom) VALUES (?, ?, ?, 'CP-1', 'Payeur 1')",
                PAYEUR_1, CENTRE_A, AGENCE_1);
        jdbc.update("UPDATE patients SET centre_payeur_id = ? WHERE center_id = ?", PAYEUR_1, CENTRE_A);
        importer("soldes-ouverture", ENTETE + "F2026-01;P1;31/01/2026;67200;20000\n");
        goldenNode("payeur_et_agence_figes", etat());
        golden("reglement_recherche_avec_payeur", appeler(get("/api/v1/reglements").param("centerId", CENTRE_A.toString())
                .param("year", "2026"), adminA()));
    }

    @Test
    void les_routes_du_reglement_servent_les_factures_de_reprise() throws Exception {
        importer("soldes-ouverture", SOLDES);
        nommerLesIdentifiants();
        UUID f1 = jdbc.queryForObject("SELECT id FROM factures WHERE center_id = ? AND numero_facture = 'REPRISE-F2026-01'",
                UUID.class, CENTRE_A);
        golden("reglement_recherche_2026", appeler(get("/api/v1/reglements").param("centerId", CENTRE_A.toString())
                .param("year", "2026"), adminA()));
        golden("reglement_tableau_de_bord_2026", appeler(get("/api/v1/reglements/dashboard").param("centerId", CENTRE_A.toString())
                .param("year", "2026"), adminA()));
        golden("reglement_historique_facture_de_reprise", appeler(get("/api/v1/reglements/{id}/paiements", f1)
                .param("centerId", CENTRE_A.toString()), adminA()));
    }

    @Test
    void un_encaissement_de_la_plateforme_interdit_la_reprise_et_l_annulation_de_la_facture() throws Exception {
        importer("soldes-ouverture", SOLDES);
        nommerLesIdentifiants();
        UUID f1 = jdbc.queryForObject("SELECT id FROM factures WHERE center_id = ? AND numero_facture = 'REPRISE-F2026-01'",
                UUID.class, CENTRE_A);
        UUID caissier = UUID.fromString("99997600-0000-0000-0000-0000000000c5");
        nommer(caissier, "<CAISSIER>");
        Resp paiement = appeler(post("/api/v1/reglements/{id}/paiements", f1), asUtilisateur(caissier, CENTRE_A, "ADMIN"),
                json("{'centerId':'" + CENTRE_A + "','montant':1000.00,'dateReglement':'2026-04-01','userId':'caissier'}"));
        golden("encaissement_plateforme", paiement);
        ObjectNode avant = etat();
        golden("rejeu_apres_encaissement", importer("soldes-ouverture", SOLDES));
        golden("annulation_apres_encaissement", annuler());
        nommerLesIdentifiants();
        assertThat(etat()).as("ni le rejeu ni l'annulation ne touchent aux factures").isEqualTo(avant);
        goldenNode("etat_apres_refus", etat());
    }

    @Test
    void reimporter_un_solde_non_encaisse_remplace_la_facture_sa_ligne_et_son_reglement_de_reprise() throws Exception {
        importer("soldes-ouverture", SOLDES);
        UUID avant = jdbc.queryForObject("SELECT id FROM factures WHERE center_id = ? AND numero_facture = 'REPRISE-F2026-01'",
                UUID.class, CENTRE_A);
        Resp rejeu = importer("soldes-ouverture", ENTETE + "F2026-01;P1;20/02/2026;50000;10000\n");
        golden("rejeu_reponse", rejeu);
        goldenNode("rejeu_etat", etat());
        UUID apres = jdbc.queryForObject("SELECT id FROM factures WHERE center_id = ? AND numero_facture = 'REPRISE-F2026-01'",
                UUID.class, CENTRE_A);
        assertThat(apres).as("la facture est mise a jour sur place (meme identifiant)").isEqualTo(avant);
    }

    @Test
    void le_rebuild_de_la_comptabilite_exclut_les_factures_et_reglements_de_reprise() throws Exception {
        importer("soldes-ouverture", SOLDES);
        nommerLesIdentifiants();
        golden("compta_non_comptabilisees_avant_nouveau_code", appeler(get("/api/v1/comptabilite/ventes/non-comptabilisees")
                .param("from", "2026-01-01").param("to", "2026-03-31"), adminA()));
        golden("compta_rebuild_nouveau_code", appeler(post("/api/v1/comptabilite/rebuild"), adminA(),
                json("{'from':'2026-01-01','to':'2026-03-31'}")));
        ObjectNode o = mapper.createObjectNode();
        o.set("ecritures", lignes("SELECT e.journal_code, e.date_ecriture, e.statut, e.libelle, l.compte_scf, l.montant_debit, "
                + "l.montant_credit FROM ecritures_comptables e JOIN lignes_ecriture l ON l.ecriture_id = e.id "
                + "WHERE e.center_id = ? ORDER BY e.date_ecriture, e.libelle, l.compte_scf, l.montant_debit, l.montant_credit",
                CENTRE_A));
        goldenNode("compta_ecritures_nouveau_code", o);
    }

    @Test
    void annuler_le_lot_ne_supprime_que_les_factures_de_reprise_du_lot() throws Exception {
        importer("soldes-ouverture", SOLDES);
        UUID ordinaire = UUID.fromString("99997600-0000-0000-0000-0000000f00aa");
        facture(ordinaire, "<FACTURE_ORDINAIRE>", CENTRE_A, PATIENT_2, "FAC-0001", "2026-02-01", "2026-02-28", "2026-02-28", "3500.00");
        jdbc.update("INSERT INTO facture_reglements (id, facture_id, center_id, montant, date_reglement, saisi_par, code_reglement, "
                + "created_at) VALUES (?, ?, ?, 100.00, ?, 'caissier', 'REG-00000001', CURRENT_TIMESTAMP)",
                UUID.fromString("99997600-0000-0000-0000-0000000d00aa"), ordinaire, CENTRE_A, date("2026-03-01"));
        nommer(UUID.fromString("99997600-0000-0000-0000-0000000d00aa"), "<REGLEMENT_ORDINAIRE>");
        golden("annulation_reponse", annuler());
        goldenNode("annulation_etat", etat());
    }

    @Test
    void le_lien_vers_une_facture_suffit_a_bloquer_l_annulation_le_statut_facturee_seul_ne_bloque_pas() throws Exception {
        importer("seances", "Patient;Date de séance;Facturée\nP1;05/01/2026;oui\nP1;07/01/2026;non\n");
        ObjectNode o = mapper.createObjectNode();
        o.set("apresImport", lignes("SELECT date_seance, statut, facture_id FROM seances WHERE center_id = ? ORDER BY date_seance",
                CENTRE_A));
        // séance « non facturée » par son statut mais rattachée à une facture : l'annulation est bloquée
        UUID facture = UUID.fromString("99997600-0000-0000-0000-0000000f00bb");
        facture(facture, "<FACTURE_1>", CENTRE_A, PATIENT_2, "FAC-0001", "2026-01-01", "2026-01-31", "2026-01-31", "100.00");
        jdbc.update("UPDATE seances SET facture_id = ? WHERE center_id = ? AND date_seance = ?", facture, CENTRE_A, date("2026-01-07"));
        o.set("annulationBloqueeParLeLien", mapper.readTree(annuler().raw()));
        o.set("seancesApres", lignes("SELECT date_seance, statut, facture_id FROM seances WHERE center_id = ? ORDER BY date_seance",
                CENTRE_A));
        // le statut FACTUREE sans lien (séance reprise « oui ») ne bloque pas
        jdbc.update("UPDATE seances SET facture_id = NULL WHERE center_id = ?", CENTRE_A);
        o.set("annulationAvecStatutSeul", mapper.readTree(annuler().raw()));
        o.put("seancesRestantes", compter("seances", CENTRE_A));
        goldenNode("annulation_selon_lien_ou_statut", o);
    }
}
