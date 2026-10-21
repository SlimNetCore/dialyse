package com.hemodialyse.backend.characterization.facturation.pilotage;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * F5 : {@code GET /api/v1/facturation/dashboard?centerId&month=yyyy-MM} (chiffre d'affaires TTC/HT, séances facturées,
 * patients facturés, factures créées, répartitions par caisse et par situation du patient).
 */
class TableauDeBordFacturationCharacterizationIntegrationTest extends PilotageFacturationSupport {

    @Override
    protected String theme() {
        return "tableau-de-bord";
    }

    /**
     * Février 2026 du centre A : cinq factures (bornes 01 et 28, une sans caisse ni situation, une situation blanche),
     * des factures hors mois (31 janvier, 1er mars), une facture du centre B, des séances facturées avec et sans facture.
     */
    private void jeuDeFevrier() {
        UUID f1 = facture(CENTRE_A, P_AMRANI, "FAC-0001", "2026-02-01", "19.00", "1000.00", "190.00", "1190.00", AGENCE_1, "PERMANENT");
        UUID f2 = facture(CENTRE_A, P_AMRANI, "FAC-0002", "2026-02-28", "19.00", "500.00", "95.00", "595.00", AGENCE_1, "PERMANENT");
        UUID f3 = facture(CENTRE_A, P_BELKACEM, "FAC-0003", "2026-02-15", "0.00", "800.00", "0.00", "800.00", AGENCE_2, "VACANCIER");
        UUID f4 = facture(CENTRE_A, P_CHIKHI, "FAC-0004", "2026-02-10", "9.00", "300.00", "27.00", "327.00", null, null);
        UUID f5 = facture(CENTRE_A, P_CHIKHI, "FAC-0005", "2026-02-11", "0.00", "100.50", "0.00", "100.50", null, "  ");
        UUID janvier = facture(CENTRE_A, P_AMRANI, "FAC-0000", "2026-01-31", "0.00", "7000.00", "0.00", "7000.00", AGENCE_1, "PERMANENT");
        UUID mars = facture(CENTRE_A, P_AMRANI, "FAC-0006", "2026-03-01", "0.00", "6000.00", "0.00", "6000.00", AGENCE_1, "PERMANENT");
        facture(CENTRE_B, P_CENTRE_B, "FAC-B-0001", "2026-02-03", "0.00", "9999.00", "0.00", "9999.00", AGENCE_B, "PERMANENT");
        seance(P_AMRANI, CENTRE_A, "2026-02-03", "FACTUREE", f1);
        seance(P_AMRANI, CENTRE_A, "2026-02-04", "FACTUREE", f1);
        seance(P_AMRANI, CENTRE_A, "2026-02-05", "FACTUREE", f1);
        seance(P_AMRANI, CENTRE_A, "2026-02-20", "FACTUREE", f2);
        seance(P_AMRANI, CENTRE_A, "2026-02-21", "FACTUREE", f2);
        seance(P_BELKACEM, CENTRE_A, "2026-02-16", "FACTUREE", f3);
        // séance de janvier rattachée à une facture de février : comptée dans les répartitions, pas dans « séances facturées »
        seance(P_BELKACEM, CENTRE_A, "2026-01-30", "FACTUREE", f3);
        // séance facturée de février sans facture, et séance validée non facturée
        seance(P_CHIKHI, CENTRE_A, "2026-02-12", "FACTUREE", null);
        seance(P_CHIKHI, CENTRE_A, "2026-02-13", "VALIDEE", null);
        // séance facturée de mars rattachée à la facture de mars, et de janvier
        seance(P_AMRANI, CENTRE_A, "2026-03-02", "FACTUREE", mars);
        seance(P_AMRANI, CENTRE_A, "2026-01-30", "FACTUREE", janvier);
        // séance facturée du centre B en février (ne compte pas pour A)
        seance(P_CENTRE_B, CENTRE_B, "2026-02-04", "FACTUREE", null);
        // séances rattachées à f4 (statut non facturé) et f5 (dernière)
        seance(P_CHIKHI, CENTRE_A, "2026-02-09", "SIGNEE", f4);
        seance(P_CHIKHI, CENTRE_A, "2026-02-10", "SIGNEE", f5);
    }

    @Test
    void le_tableau_de_bord_de_fevrier_totalise_les_factures_du_mois_et_repartit_par_caisse_et_par_situation() throws Exception {
        jeuDeFevrier();

        golden("fevrier_2026_centre_a", dashboard(CENTRE_A, "2026-02", adminA()));
    }

    @Test
    void le_tableau_de_bord_inclut_le_premier_et_le_dernier_jour_du_mois_et_exclut_les_jours_voisins() throws Exception {
        jeuDeFevrier();

        JsonNode fevrier = json(dashboard(CENTRE_A, "2026-02", adminA()));
        JsonNode janvier = json(dashboard(CENTRE_A, "2026-01", adminA()));
        JsonNode mars = json(dashboard(CENTRE_A, "2026-03", adminA()));

        // TTC de février : 1190 + 595 + 800 + 327 + 100.50
        assertEquals(0, new BigDecimal("3012.50").compareTo(fevrier.path("revenueTtc").decimalValue()));
        assertEquals(0, new BigDecimal("7000.00").compareTo(janvier.path("revenueTtc").decimalValue()));
        assertEquals(0, new BigDecimal("6000.00").compareTo(mars.path("revenueTtc").decimalValue()));
        ObjectNode deux = mapper.createObjectNode();
        deux.set("janvier", janvier);
        deux.set("mars", mars);
        goldenNode("janvier_et_mars_2026", deux);
    }

    @Test
    void le_tableau_de_bord_ne_filtre_pas_par_le_centre_de_l_utilisateur() throws Exception {
        jeuDeFevrier();

        // Un administrateur du centre A interroge le centre B ; un administrateur du centre B interroge le centre A.
        // Nouveau code F5 : 403 si le centre demandé n'est pas celui de la session (KB-FAC-52 corrigé)
        golden("fevrier_2026_centre_b_vu_par_admin_a_nouveau_code", dashboard(CENTRE_B, "2026-02", adminA()));
        golden("fevrier_2026_centre_a_vu_par_admin_b_nouveau_code", dashboard(CENTRE_A, "2026-02", adminB()));
        // Nouveau code F5 : chacun lit son propre centre
        golden("fevrier_2026_centre_b_par_son_administrateur_nouveau_code", dashboard(CENTRE_B, "2026-02", adminB()));
    }

    @Test
    void un_centre_sans_facture_ou_inconnu_donne_des_zeros_et_des_listes_vides() throws Exception {
        jeuDeFevrier();

        golden("mois_sans_facture", dashboard(CENTRE_A, "2026-05", adminA()));
        // Nouveau code F5 : un centre qui n'est pas celui de la session donne 403 (KB-FAC-52 corrigé)
        golden("centre_sans_facture_nouveau_code", dashboard(CENTRE_VIDE, "2026-02", adminA()));
        golden("centre_inconnu_nouveau_code", dashboard(CENTRE_INCONNU, "2026-02", adminA()));
        // Nouveau code F5 : l'administrateur d'un centre sans facture obtient des zéros et des listes vides
        golden("centre_sans_facture_par_son_administrateur_nouveau_code", dashboard(CENTRE_VIDE, "2026-02", as(CENTRE_VIDE, "ADMIN")));
    }

    @Test
    void le_tableau_de_bord_classe_les_caisses_par_nombre_de_patients_puis_par_code_et_les_situations_par_seances() throws Exception {
        // Caisse 2 : deux patients ; caisse 1 : un patient ; situation « PERMANENT » : 3 séances, « VACANCIER » : 3 séances (égalité).
        UUID fa = facture(CENTRE_A, P_AMRANI, "FAC-0001", "2026-04-02", "0.00", "100.00", "0.00", "100.00", AGENCE_1, "PERMANENT");
        UUID fb = facture(CENTRE_A, P_BELKACEM, "FAC-0002", "2026-04-03", "0.00", "200.00", "0.00", "200.00", AGENCE_2, "VACANCIER");
        UUID fc = facture(CENTRE_A, P_CHIKHI, "FAC-0003", "2026-04-04", "0.00", "300.00", "0.00", "300.00", AGENCE_2, "PERMANENT");
        seance(P_AMRANI, CENTRE_A, "2026-04-02", "FACTUREE", fa);
        seance(P_AMRANI, CENTRE_A, "2026-04-03", "FACTUREE", fa);
        seance(P_AMRANI, CENTRE_A, "2026-04-04", "FACTUREE", fa);
        seance(P_BELKACEM, CENTRE_A, "2026-04-05", "FACTUREE", fb);
        seance(P_BELKACEM, CENTRE_A, "2026-04-06", "FACTUREE", fb);
        seance(P_BELKACEM, CENTRE_A, "2026-04-07", "FACTUREE", fb);
        seance(P_CHIKHI, CENTRE_A, "2026-04-08", "FACTUREE", fc);

        golden("classement_caisses_et_situations", dashboard(CENTRE_A, "2026-04", adminA()));
    }

    @Test
    void les_lignes_de_facture_et_les_reglements_ne_modifient_pas_le_tableau_de_bord() throws Exception {
        UUID f = facture(CENTRE_A, P_AMRANI, "FAC-0001", "2026-05-10", "19.00", "1000.00", "190.00", "1190.00", AGENCE_1, "PERMANENT");
        ligne(f, CENTRE_A, "Forfait HD", "500.00", 2, "1000.00");
        ligne(f, CENTRE_A, "Forfait Transport", "50.00", 2, "9999.00");
        paiement(f, CENTRE_A, "1190.00", "2026-05-20");
        seance(P_AMRANI, CENTRE_A, "2026-05-02", "FACTUREE", f);

        golden("avec_lignes_et_reglements", dashboard(CENTRE_A, "2026-05", adminA()));
    }

    @Test
    void une_facture_dont_le_total_ttc_n_est_pas_ht_plus_tva_est_sommee_telle_quelle() throws Exception {
        // Le tableau de bord additionne les colonnes total_ttc et total_ht stockées ; il ne les recalcule pas.
        facture(CENTRE_A, P_AMRANI, "FAC-0001", "2026-06-10", "19.00", "100.00", "19.00", "119.00", AGENCE_1, "PERMANENT");
        facture(CENTRE_A, P_BELKACEM, "FAC-0002", "2026-06-11", "19.00", "100.00", "1.00", "555.55", AGENCE_2, "PERMANENT");
        facture(CENTRE_A, P_CHIKHI, "FAC-0003", "2026-06-12", "0.00", "0.01", "0.00", "0.01", AGENCE_2, "PERMANENT");

        golden("totaux_stockes_non_recalcules", dashboard(CENTRE_A, "2026-06", adminA()));
    }

    @Test
    void le_mois_de_fevrier_d_une_annee_bissextile_va_du_1er_au_29() throws Exception {
        facture(CENTRE_A, P_AMRANI, "FAC-0001", "2024-02-29", "0.00", "10.00", "0.00", "10.00", AGENCE_1, "PERMANENT");
        facture(CENTRE_A, P_AMRANI, "FAC-0002", "2024-03-01", "0.00", "20.00", "0.00", "20.00", AGENCE_1, "PERMANENT");
        facture(CENTRE_A, P_AMRANI, "FAC-0003", "2024-01-31", "0.00", "40.00", "0.00", "40.00", AGENCE_1, "PERMANENT");

        golden("fevrier_2024_bissextile", dashboard(CENTRE_A, "2024-02", adminA()));
        golden("decembre_2025", dashboard(CENTRE_A, "2025-12", adminA()));
    }

    @Test
    void le_mois_est_obligatoire_et_son_format_est_yyyy_mm() throws Exception {
        jeuDeFevrier();
        Map<String, Object> cas = new LinkedHashMap<>();
        String[][] valeurs = {
                {"absent", null}, {"vide", ""}, {"espaces", "%20%20"}, {"2026-02 entoure d'espaces", "%202026-02%20"},
                {"2026-2", "2026-2"}, {"2026-13", "2026-13"}, {"2026-00", "2026-00"}, {"202602", "202602"},
                {"texte", "fevrier"}, {"2026-02-15", "2026-02-15"}, {"26-02", "26-02"}, {"annee 5 chiffres", "20260-02"},
                {"annee 0001", "0001-02"}, {"avec signe plus", "+2026-02"}
        };
        for (String[] v : valeurs) {
            Resp r = doGet(FACTURATION + "/dashboard?centerId=" + CENTRE_A + (v[1] == null ? "" : "&month=" + v[1]), adminA());
            cas.put(v[0], r.status() + " " + resumeErreur(r));
        }
        goldenNode("mois_invalides", mapper.valueToTree(cas));
    }

    @Test
    void le_centre_est_obligatoire_et_doit_etre_un_identifiant() throws Exception {
        jeuDeFevrier();
        Map<String, Object> cas = new LinkedHashMap<>();
        String[][] chemins = {
                {"centre absent", FACTURATION + "/dashboard?month=2026-02"},
                {"centre vide", FACTURATION + "/dashboard?centerId=&month=2026-02"},
                {"centre illisible", FACTURATION + "/dashboard?centerId=abc&month=2026-02"},
                {"aucun parametre", FACTURATION + "/dashboard"},
                {"parametre supplementaire ignore", FACTURATION + "/dashboard?centerId=" + CENTRE_VIDE + "&month=2026-02&x=1"},
                {"centre repete", FACTURATION + "/dashboard?centerId=" + CENTRE_VIDE + "&centerId=" + CENTRE_A + "&month=2026-02"}
        };
        for (String[] c : chemins) {
            Resp r = doGet(c[1], adminA());
            cas.put(c[0], r.status() + " " + resumeErreur(r));
        }
        // Nouveau code F5 : 403 pour un centre qui n'est pas celui de la session (KB-FAC-52) ; le reste est inchangé
        goldenNode("centre_invalide_nouveau_code", mapper.valueToTree(cas));
    }

    @Test
    void le_tableau_de_bord_n_accepte_que_get_et_repond_selon_le_profil() throws Exception {
        jeuDeFevrier();
        Map<String, Object> cas = new LinkedHashMap<>();
        String chemin = FACTURATION + "/dashboard?centerId=" + CENTRE_A + "&month=2026-02";
        cas.put("ADMIN", doGet(chemin, as(CENTRE_A, "ADMIN")).status());
        cas.put("SECRETAIRE", doGet(chemin, as(CENTRE_A, "SECRETAIRE")).status());
        cas.put("MEDECIN seul", doGet(chemin, as(CENTRE_A, "MEDECIN")).status());
        cas.put("MEDECIN + SECRETAIRE", doGet(chemin, as(CENTRE_A, "MEDECIN", "SECRETAIRE")).status());
        cas.put("INFIRMIER seul", doGet(chemin, as(CENTRE_A, "INFIRMIER")).status());
        cas.put("INFIRMIER + ADMIN", doGet(chemin, as(CENTRE_A, "INFIRMIER", "ADMIN")).status());
        cas.put("COMPTABLE", doGet(chemin, as(CENTRE_A, "COMPTABLE")).status());
        cas.put("DIRECTION", doGet(chemin, direction()).status());
        cas.put("SUPERADMIN", doGet(chemin, as(CENTRE_A, "SUPERADMIN")).status());
        cas.put("aucun role", doGet(chemin, as(CENTRE_A)).status());
        cas.put("anonyme", doGetAnonyme(chemin).status());
        cas.put("POST ADMIN", doPost(FACTURATION + "/dashboard", "{}", adminA()).status());
        goldenNode("profils", mapper.valueToTree(cas));
    }

    @Test
    void les_reponses_refusees_portent_le_code_de_refus() throws Exception {
        jeuDeFevrier();
        String chemin = FACTURATION + "/dashboard?centerId=" + CENTRE_A + "&month=2026-02";

        golden("refus_medecin_seul", doGet(chemin, as(CENTRE_A, "MEDECIN")));
        golden("refus_infirmier", doGet(chemin, as(CENTRE_A, "INFIRMIER")));
        golden("refus_direction", doGet(chemin, direction()));
        golden("refus_anonyme", doGetAnonyme(chemin));
    }

    /**
     * Résumé d'une réponse : code d'erreur (et message lisible quand il est métier ; les messages techniques du JDK ou de
     * Spring ne sont pas verrouillés) ou, en succès, le centre et le mois renvoyés.
     */
    private String resumeErreur(Resp r) {
        if (r.raw() == null || r.raw().isBlank()) return "(corps vide)";
        JsonNode n = mapper.readTree(r.raw());
        if (r.status() == 200) return "centre=" + new Scrubber().scrubString(n.path("centerId").asString()) + " mois=" + n.path("month").asString();
        String code = n.path("code").asString("");
        boolean technique = "INTERNAL_ERROR".equals(code);
        return code + (technique ? "" : " : " + n.path("detail").asString());
    }

    private static final UUID CENTRE_INCONNU = nom("CENTRE_INCONNU");
}
