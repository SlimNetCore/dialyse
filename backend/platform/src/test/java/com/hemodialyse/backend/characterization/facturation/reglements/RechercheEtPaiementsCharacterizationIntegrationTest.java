package com.hemodialyse.backend.characterization.facturation.reglements;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * F4 : {@code GET /api/v1/reglements} (recherche paginée par année/mois, caisse, agence, centre payeur) et
 * {@code GET /api/v1/reglements/{factureId}/paiements} (historique d'une facture).
 */
class RechercheEtPaiementsCharacterizationIntegrationTest extends ReglementsCharacterizationSupport {

    private UUID f1, f2, f3, f4, f5, f6, f7, fB;

    @Override
    protected String theme() {
        return "recherche";
    }

    /**
     * Sept factures du centre A : trois en janvier/février, une en décembre 2025 (autre année), une sans instantané de
     * payeur (le payeur du patient s'applique), une dont l'instantané diffère du payeur courant du patient ; une
     * facture du centre B.
     */
    private void jeuDeFactures() {
        f1 = facture(CENTRE_A, P_AMRANI, "FAC-0001", "2026-01-31", "1000.00", PAYEUR_1, AGENCE_1);
        f2 = facture(CENTRE_A, P_BELKACEM, "FAC-0002", "2026-02-01", "500.00", PAYEUR_2, AGENCE_2);
        f3 = facture(CENTRE_A, P_CHIKHI, "FAC-0003", "2026-02-01", "800.00", null, null);
        f4 = facture(CENTRE_A, P_AMRANI, "FAC-0004", "2026-02-28", "1200.50", PAYEUR_1, AGENCE_1);
        f5 = facture(CENTRE_A, P_AMRANI, "FAC-0000", "2025-12-31", "300.00", PAYEUR_1, AGENCE_1);
        f6 = facture(CENTRE_A, P_BELKACEM, "FAC-0006", "2026-03-15", "100.00", null, null);
        f7 = facture(CENTRE_A, P_BELKACEM, "FAC-0007", "2026-03-15", "250.00", PAYEUR_1, AGENCE_1);
        fB = facture(CENTRE_B, P_CENTRE_B, "FAC-B-0001", "2026-02-01", "900.00", PAYEUR_B, AGENCE_B);
        paiement(f1, CENTRE_A, "400.00", "2026-02-10", "u1", "REG-SEED-001", "2026-02-10 09:00:00");
        paiement(f2, CENTRE_A, "500.00", "2026-02-11", "u1", "REG-SEED-002", "2026-02-11 09:00:00");
        paiement(f4, CENTRE_A, "1300.00", "2026-03-01", "u2", "REG-SEED-003", "2026-03-01 09:00:00");
        paiement(fB, CENTRE_B, "900.00", "2026-02-02", "u3", "REG-SEED-004", "2026-02-02 09:00:00");
    }

    private String q(String suite) {
        return "?centerId=" + CENTRE_A + "&" + suite;
    }

    /**
     * Vue compacte d'une recherche : statut, pagination et, par ligne, numéro, état, solde.
     */
    private void goldenCompact(String name, Resp r) {
        ObjectNode o = mapper.createObjectNode();
        o.put("status", r.status());
        JsonNode corps = json(r);
        if (corps.has("items")) {
            o.set("total", corps.path("total"));
            o.set("page", corps.path("page"));
            o.set("size", corps.path("size"));
            ArrayNode lignes = o.putArray("lignes");
            for (JsonNode i : corps.path("items")) {
                lignes.add(i.path("numeroFacture").asString() + " | " + i.path("dateFacturation").asString() + " | "
                        + i.path("etat").asString() + "/" + i.path("soldeType").asString() + " | facture "
                        + i.path("montantFacture") + " | regle " + i.path("montantRegle") + " | payeur "
                        + i.path("centrePayeur").asString() + " | agence " + i.path("agence").asString() + " | caisse "
                        + i.path("caisse").asString());
            }
        } else {
            o.set("body", corps);
        }
        goldenNode(name, o);
    }

    @Test
    void la_recherche_liste_les_factures_de_l_annee_du_centre_de_la_plus_recente_a_la_plus_ancienne() throws Exception {
        jeuDeFactures();

        golden("annee_2026_complete", doGet(q("year=2026"), adminA()));
    }

    @Test
    void le_tri_est_par_date_de_facturation_decroissante_puis_numero_decroissant() throws Exception {
        jeuDeFactures();

        goldenCompact("annee_2026_tri", doGet(q("year=2026"), adminA()));
    }

    @Test
    void le_filtre_par_mois_retient_les_bornes_du_premier_au_dernier_jour_inclus() throws Exception {
        jeuDeFactures();

        goldenCompact("mois_janvier", doGet(q("year=2026&month=1"), adminA()));
        goldenCompact("mois_fevrier", doGet(q("year=2026&month=2"), adminA()));
        goldenCompact("mois_decembre_2025", doGet(q("year=2025&month=12"), adminA()));
        goldenCompact("mois_avril_vide", doGet(q("year=2026&month=4"), adminA()));
        goldenCompact("annee_2025", doGet(q("year=2025"), adminA()));
    }

    @Test
    void le_mois_de_fevrier_d_une_annee_bissextile_va_jusqu_au_29() throws Exception {
        facture(CENTRE_A, P_AMRANI, "FAC-BIS-29", "2028-02-29", "10.00", PAYEUR_1, AGENCE_1);
        facture(CENTRE_A, P_AMRANI, "FAC-BIS-01-03", "2028-03-01", "10.00", PAYEUR_1, AGENCE_1);

        goldenCompact("fevrier_bissextile", doGet(q("year=2028&month=2"), adminA()));
    }

    @Test
    void les_filtres_par_caisse_agence_et_centre_payeur_utilisent_l_instantane_de_la_facture_puis_le_patient() throws Exception {
        jeuDeFactures();

        goldenCompact("filtre_caisse_1", doGet(q("year=2026&caisseId=" + CAISSE_1), adminA()));
        goldenCompact("filtre_caisse_2", doGet(q("year=2026&caisseId=" + CAISSE_2), adminA()));
        goldenCompact("filtre_agence_2", doGet(q("year=2026&agenceId=" + AGENCE_2), adminA()));
        goldenCompact("filtre_payeur_1", doGet(q("year=2026&centrePayeurId=" + PAYEUR_1), adminA()));
        goldenCompact("filtres_combines_sans_resultat", doGet(q("year=2026&caisseId=" + CAISSE_1 + "&centrePayeurId=" + PAYEUR_2), adminA()));
        goldenCompact("filtre_inconnu", doGet(q("year=2026&caisseId=" + UUID.randomUUID()), adminA()));
        goldenCompact("filtre_payeur_d_un_autre_centre", doGet(q("year=2026&centrePayeurId=" + PAYEUR_B), adminA()));
    }

    @Test
    void sans_instantane_de_payeur_la_facture_prend_le_payeur_courant_du_patient_sinon_non_renseigne() throws Exception {
        jeuDeFactures();

        golden("instantanes_absents", doGet(q("year=2026&month=3"), adminA()));
    }

    @Test
    void la_pagination_decoupe_la_liste_triee_et_garde_le_total() throws Exception {
        jeuDeFactures();

        goldenCompact("page_0_taille_2", doGet(q("year=2026&page=0&size=2"), adminA()));
        goldenCompact("page_1_taille_2", doGet(q("year=2026&page=1&size=2"), adminA()));
        goldenCompact("page_2_taille_2", doGet(q("year=2026&page=2&size=2"), adminA()));
        goldenCompact("page_hors_limites", doGet(q("year=2026&page=9&size=2"), adminA()));
        goldenCompact("taille_enorme", doGet(q("year=2026&size=1000000"), adminA()));
    }

    @Test
    void le_total_compte_les_factures_et_non_les_lignes_de_reglement() throws Exception {
        jeuDeFactures();
        paiement(f1, CENTRE_A, "100.00", "2026-02-12", "u1", "REG-SEED-010", "2026-02-12 09:00:00");
        paiement(f1, CENTRE_A, "50.00", "2026-02-13", "u1", "REG-SEED-011", "2026-02-13 09:00:00");

        goldenCompact("total_avec_plusieurs_reglements", doGet(q("year=2026&month=1&size=1"), adminA()));
        golden("facture_a_trois_reglements", doGet(q("year=2026&month=1"), adminA()));
    }

    @Test
    void les_parametres_hors_bornes_sont_refuses() throws Exception {
        jeuDeFactures();

        golden("annee_1999", doGet(q("year=1999"), adminA()));
        golden("annee_2000_ok", doGet(q("year=2000"), adminA()));
        golden("annee_3000_ok", doGet(q("year=3000"), adminA()));
        golden("annee_3001", doGet(q("year=3001"), adminA()));
        golden("mois_0", doGet(q("year=2026&month=0"), adminA()));
        golden("mois_13", doGet(q("year=2026&month=13"), adminA()));
        golden("page_negative", doGet(q("year=2026&page=-1"), adminA()));
        golden("taille_zero", doGet(q("year=2026&size=0"), adminA()));
        golden("taille_negative", doGet(q("year=2026&size=-5"), adminA()));
    }

    @Test
    void les_parametres_manquants_ou_illisibles_sont_refuses() throws Exception {
        jeuDeFactures();

        golden("sans_annee", doGet(q(""), adminA()));
        golden("sans_centre", doGet("?year=2026", adminA()));
        golden("annee_texte", doGet(q("year=abc"), adminA()));
        golden("centre_non_uuid", doGet("?centerId=xyz&year=2026", adminA()));
        golden("mois_texte", doGet(q("year=2026&month=jan"), adminA()));
        golden("caisse_non_uuid", doGet(q("year=2026&caisseId=xyz"), adminA()));
        golden("page_texte", doGet(q("year=2026&page=x"), adminA()));
    }

    @Test
    void la_recherche_n_expose_pas_les_factures_d_un_autre_centre_meme_si_le_centre_est_demande() throws Exception {
        jeuDeFactures();

        goldenCompact("centre_a", doGet(q("year=2026&month=2"), adminA()));
        // KB-FAC-37 : le centre est un paramètre de la requête, jamais comparé à celui de la session.
        // Nouveau code F4 (KB-FAC-37 corrigé) : 403 quand le centre demandé n'est pas celui de la session.
        goldenCompact("centre_b_demande_par_un_admin_du_centre_a_nouveau_code",
                doGet("?centerId=" + CENTRE_B + "&year=2026", adminA()));
    }

    /**
     * LEC-PAT-GMAO (3), KB-FAC-49 : la fiche du patient (numéro d'assurance, nom, prénom, payeur courant) n'est lue que si le patient est du
     * centre de la facture ; un patient d'un autre centre ou inconnu retombe sur l'instantané de la facture (numéro d'immatriculation, nom
     * complet, payeur figé) ou sur « - » / « Non renseigne ».
     */
    @Test
    void la_fiche_d_un_patient_d_un_autre_centre_ou_inconnu_n_est_pas_lue_et_l_instantane_de_la_facture_s_applique() throws Exception {
        facture(CENTRE_A, P_AMRANI, "FAC-P-01", "2026-04-10", "100.00", PAYEUR_1, AGENCE_1);
        facture(CENTRE_A, P_CENTRE_B, "FAC-P-02", "2026-04-09", "200.00", null, null);
        facture(CENTRE_A, P_CENTRE_B, "FAC-P-03", "2026-04-08", "300.00", PAYEUR_2, AGENCE_2);
        facture(CENTRE_A, UUID.randomUUID(), "FAC-P-04", "2026-04-07", "400.00", null, null);
        jdbc.update("UPDATE patients SET prenom = '' WHERE id = ?", P_BELKACEM);
        facture(CENTRE_A, P_BELKACEM, "FAC-P-05", "2026-04-06", "500.00", null, null);

        golden("patient_d_un_autre_centre_ou_inconnu", doGet(q("year=2026&month=4"), adminA()));
        goldenCompact("patient_d_un_autre_centre_ou_inconnu_compact", doGet(q("year=2026&month=4&centrePayeurId=" + PAYEUR_2), adminA()));
    }

    /** KB-FAC-49 : le tri date puis numéro n'a jamais d'égalité dans un centre, la base refuse deux fois le même numéro au même centre. */
    @Test
    void la_base_refuse_deux_factures_de_meme_numero_dans_un_centre_donc_le_tri_date_numero_est_total() throws Exception {
        facture(CENTRE_A, P_AMRANI, "FAC-EGAL", "2026-04-10", "100.00", PAYEUR_1, AGENCE_1);

        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DuplicateKeyException.class,
                () -> facture(CENTRE_A, P_BELKACEM, "FAC-EGAL", "2026-04-10", "200.00", PAYEUR_1, AGENCE_1));
        // le même numéro est permis dans un AUTRE centre, que la recherche d'un centre ne voit pas
        facture(CENTRE_B, P_CENTRE_B, "FAC-EGAL", "2026-04-10", "300.00", PAYEUR_B, AGENCE_B);
        assertEquals(1, json(doGet(q("year=2026&month=4"), adminA())).path("items").size());
    }

    @Test
    void le_dernier_code_de_reglement_est_celui_du_reglement_cree_en_dernier() throws Exception {
        UUID facture = factureMille();
        paiement(facture, CENTRE_A, "100.00", "2026-02-20", "u", "REG-SEED-A", "2026-02-01 08:00:00");
        paiement(facture, CENTRE_A, "100.00", "2026-02-05", "u", "REG-SEED-B", "2026-02-25 08:00:00");
        paiement(facture, CENTRE_A, "100.00", "2026-02-25", "u", "REG-SEED-C", "2026-02-10 08:00:00");

        Resp r = doGet(q("year=2026"), adminA());

        assertEquals("REG-SEED-B", json(r).path("items").get(0).path("latestCodeReglement").asString());
        assertEquals(3, json(r).path("items").get(0).path("paymentCount").asInt());
    }

    // ------------------------------------------------------------------ paiements d'une facture

    @Test
    void l_historique_des_paiements_est_trie_par_date_de_reglement_puis_creation_decroissantes() throws Exception {
        UUID facture = factureMille();
        paiement(facture, CENTRE_A, "100.00", "2026-02-10", "u1", "REG-SEED-A", "2026-02-10 08:00:00");
        paiement(facture, CENTRE_A, "200.00", "2026-02-20", "u2", "REG-SEED-B", "2026-02-01 08:00:00");
        paiement(facture, CENTRE_A, "-50.00", "2026-02-10", "u3", "REG-SEED-C", "2026-02-11 08:00:00");
        paiement(facture, CENTRE_A, "300.00", "2026-01-05", "u4", null, "2026-01-05 08:00:00");

        golden("historique_trie", doGet("/" + facture + "/paiements?centerId=" + CENTRE_A, adminA()));
    }

    @Test
    void l_historique_n_a_ni_total_ni_situation_ni_pagination() throws Exception {
        UUID facture = factureMille();
        paiement(facture, CENTRE_A, "100.00", "2026-02-10", "u1", "REG-SEED-A", "2026-02-10 08:00:00");

        Resp r = doGet("/" + facture + "/paiements?centerId=" + CENTRE_A, adminA());

        assertEquals(true, json(r).isArray());
        java.util.TreeSet<String> cles = new java.util.TreeSet<>();
        json(r).get(0).properties().forEach(e -> cles.add(e.getKey()));
        assertEquals("[codeReglement, dateReglement, factureId, id, montant, saisiPar]", cles.toString());
    }

    @Test
    void l_historique_d_une_facture_sans_reglement_inconnue_ou_d_un_autre_centre_est_une_liste_vide_en_200() throws Exception {
        UUID facture = factureMille();
        UUID factureB = facture(CENTRE_B, P_CENTRE_B, "FAC-B-0001", "2026-01-31", "500.00", PAYEUR_B, AGENCE_B);
        paiement(factureB, CENTRE_B, "10.00", "2026-02-01", "u", "REG-SEED-B", "2026-02-01 08:00:00");

        golden("historique_sans_reglement", doGet("/" + facture + "/paiements?centerId=" + CENTRE_A, adminA()));
        golden("historique_facture_inconnue", doGet("/" + UUID.randomUUID() + "/paiements?centerId=" + CENTRE_A, adminA()));
        golden("historique_facture_autre_centre", doGet("/" + factureB + "/paiements?centerId=" + CENTRE_A, adminA()));
        // KB-FAC-37 : le centre B demandé par un administrateur du centre A est servi.
        // Nouveau code F4 (KB-FAC-37 corrigé) : 403 quand le centre demandé n'est pas celui de la session.
        golden("historique_centre_b_demande_par_admin_a_nouveau_code", doGet("/" + factureB + "/paiements?centerId=" + CENTRE_B, adminA()));
    }

    @Test
    void l_historique_refuse_un_centre_manquant_ou_un_identifiant_illisible() throws Exception {
        UUID facture = factureMille();

        golden("historique_sans_centre", doGet("/" + facture + "/paiements", adminA()));
        golden("historique_facture_non_uuid", doGet("/pas-un-uuid/paiements?centerId=" + CENTRE_A, adminA()));
    }

    @Test
    void un_reglement_enregistre_apparait_aussitot_dans_l_historique_et_dans_la_recherche() throws Exception {
        UUID facture = factureMille();
        regler(facture, "125.50", "2026-02-10");

        // Nouveau code F4 : auteur = utilisateur de la session.
        golden("apres_enregistrement_historique_nouveau_code", doGet("/" + facture + "/paiements?centerId=" + CENTRE_A, adminA()));
        goldenCompact("apres_enregistrement_recherche", doGet(q("year=2026"), adminA()));
    }
}
