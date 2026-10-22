package com.hemodialyse.backend.characterization.absencespatients.cyclevie;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Slice absences-patients-a2-cycle-de-vie, points d'argent : valorisation figee a la declaration. Forfait HT de la prise en
 * charge (effectif d'abord, puis demande ; la plus recente), TTC = HT * (1 + taux / 100) arrondi a 2 decimales HALF_UP, taux du
 * type de TVA {@code HEMODIALYSE} de la date de la seance manquee (0 si regle exoneree ou absente, KB-CPT-18 / KB-CPT-19), jamais
 * recalculee. Observation : champs {@code forfaitLibelle}, {@code montantHt}, {@code tauxTva}, {@code prixTtc} de la reponse
 * et colonnes de {@code absence_patient}.
 */
class ValorisationFigeDesAbsencesCharacterizationIntegrationTest extends CycleDeVieAbsencesCharacterizationSupport {

    private int rang;

    @Override
    protected String theme() {
        return "valorisation";
    }

    private ObjectNode valeur(Resp r) {
        ObjectNode o = mapper.createObjectNode();
        o.put("status", r.status());
        if (r.status() == 201) {
            var c = json(r);
            o.set("forfaitLibelle", c.path("forfaitLibelle"));
            o.set("montantHt", c.path("montantHt"));
            o.set("tauxTva", c.path("tauxTva"));
            o.set("prixTtc", c.path("prixTtc"));
        } else {
            o.set("refus", refus(r));
        }
        return o;
    }

    /** Patient neuf du centre A sans prise en charge. */
    private UUID nouveauPatient() {
        rang++;
        UUID p = UUID.randomUUID();
        patient(p, CENTRE_A, "ABS2-V" + rang, "Valo" + rang, "Test");
        return p;
    }

    private UUID nouveauForfait(String prix) {
        UUID f = UUID.randomUUID();
        forfait(f, CENTRE_A, "FV" + (++rang), "Forfait " + prix, prix);
        return f;
    }

    @Test
    void le_ttc_est_le_ht_multiplie_par_un_plus_le_taux_arrondi_a_deux_decimales_au_demi_superieur() throws Exception {
        String[][] cas = {
                {"10000.00", "19.00"}, {"10.10", "5.00"}, {"3333.33", "19.00"}, {"100.05", "9.50"}, {"0.00", "19.00"},
                {"4500.00", "0.00"}, {"0.01", "19.00"}, {"0.05", "10.00"}, {"12345.67", "7.00"}, {"99999999.99", "19.00"},
                {"2500.50", "9.00"}};
        ArrayNode lignes = mapper.createArrayNode();
        int jours = 1;
        for (String[] c : cas) {
            supprimerLaTva(CENTRE_A);
            tva(CENTRE_A, c[1], false, "HEMODIALYSE", "2000-01-01", null);
            UUID p = nouveauPatient();
            pec(p, CENTRE_A, "2020-01-01", null, null, null, null, nouveauForfait(c[0]), 1);
            ObjectNode l = mapper.createObjectNode();
            l.put("forfaitHt", c[0]);
            l.put("taux", c[1]);
            l.set("valeur", valeur(declarer(admin(), p, jour(jours++), null, null)));
            lignes.add(l);
        }
        goldenNode("formule_et_arrondis", lignes);
    }

    @Test
    void sans_prise_en_charge_la_valeur_est_nulle_mais_le_taux_est_releve() throws Exception {
        supprimerLesPec(P_AMRANI);
        ObjectNode o = mapper.createObjectNode();
        o.set("valeur", valeur(declarer(admin(), P_AMRANI, jour(3), null, null)));
        o.set("base", etat(CENTRE_A));
        goldenNode("sans_prise_en_charge", o);
    }

    @Test
    void sans_regle_de_tva_le_taux_est_zero_et_le_ttc_egale_le_ht() throws Exception {
        supprimerLaTva(CENTRE_A);
        ObjectNode o = mapper.createObjectNode();
        o.set("valeur", valeur(declarer(admin(), P_AMRANI, jour(3), null, null)));
        o.set("base", etat(CENTRE_A));
        goldenNode("sans_regle_de_tva_nouveau_code", o);
    }

    @Test
    void une_regle_exoneree_donne_un_taux_de_zero_alors_que_la_facturation_applique_le_taux_kb_cpt_19() throws Exception {
        supprimerLaTva(CENTRE_A);
        tva(CENTRE_A, "19.00", true, "HEMODIALYSE", "2000-01-01", null);
        ObjectNode o = mapper.createObjectNode();
        o.set("valeur", valeur(declarer(admin(), P_AMRANI, jour(3), null, null)));
        o.set("base", etat(CENTRE_A));
        goldenNode("regle_exoneree_nouveau_code", o);
    }

    @Test
    void seul_le_type_hemodialyse_exact_est_retenu_kb_cpt_18() throws Exception {
        ObjectNode o = mapper.createObjectNode();
        supprimerLaTva(CENTRE_A);
        tva(CENTRE_A, "9.00", false, "AUTRE_PRESTATION", "2000-01-01", null);
        o.set("autre_type_seul", valeur(declarer(admin(), P_AMRANI, jour(1), null, null)));
        supprimerLaTva(CENTRE_A);
        tva(CENTRE_A, "9.00", false, "hemodialyse", "2000-01-01", null);
        o.set("type_en_minuscules", valeur(declarer(admin(), P_AMRANI, jour(2), null, null)));
        supprimerLaTva(CENTRE_A);
        tva(CENTRE_A, "9.00", false, "AUTRE_PRESTATION", "2000-01-01", null);
        tva(CENTRE_A, "7.00", false, "HEMODIALYSE", "2000-01-01", null);
        o.set("autre_type_et_hemodialyse", valeur(declarer(admin(), P_AMRANI, jour(3), null, null)));
        goldenNode("type_de_prestation_nouveau_code", o);
    }

    @Test
    void le_taux_est_celui_de_la_regle_en_vigueur_le_jour_de_la_seance_manquee() throws Exception {
        supprimerLaTva(CENTRE_A);
        tva(CENTRE_A, "9.00", false, "HEMODIALYSE", "2000-01-01", jour(20).toString());
        tva(CENTRE_A, "19.00", false, "HEMODIALYSE", jour(19).toString(), null);
        ObjectNode o = mapper.createObjectNode();
        o.set("il_y_a_21_jours_ancienne_regle", valeur(declarer(admin(), P_AMRANI, jour(21), null, null)));
        o.set("il_y_a_20_jours_dernier_jour_ancienne_regle", valeur(declarer(admin(), P_AMRANI, jour(20), null, null)));
        o.set("il_y_a_19_jours_premier_jour_nouvelle_regle", valeur(declarer(admin(), P_AMRANI, jour(19), null, null)));
        o.set("il_y_a_5_jours_nouvelle_regle", valeur(declarer(admin(), P_AMRANI, jour(5), null, null)));
        goldenNode("taux_selon_la_date", o);
    }

    @Test
    void la_regle_de_tva_d_un_autre_centre_n_est_pas_utilisee() throws Exception {
        supprimerLaTva(CENTRE_A);
        ObjectNode o = mapper.createObjectNode();
        o.set("centre_A_sans_regle_centre_B_a_9", valeur(declarer(admin(), P_AMRANI, jour(3), null, null)));
        goldenNode("taux_d_un_autre_centre_nouveau_code", o);
    }

    @Test
    void le_forfait_effectif_prime_sur_le_forfait_demande() throws Exception {
        supprimerLesPec(P_AMRANI);
        pec(P_AMRANI, CENTRE_A, "2020-01-01", null, "2020-01-01", null, F_PREMIUM, F_HD, 1);
        supprimerLesPec(P_BELKACEM);
        pec(P_BELKACEM, CENTRE_A, null, null, "2020-01-01", null, F_PREMIUM, null, 1);
        supprimerLesPec(P_CHIKHI);
        // l'effectif ne couvre pas la date (commence apres) mais la demande oui : le forfait EFFECTIF est tout de meme pris
        pec(P_CHIKHI, CENTRE_A, jour(-30).toString(), null, "2020-01-01", null, F_PREMIUM, F_HD, 1);
        ObjectNode o = mapper.createObjectNode();
        o.set("effectif_et_demande_couvrent", valeur(declarer(admin(), P_AMRANI, jour(3), null, null)));
        o.set("seule_la_demande", valeur(declarer(admin(), P_BELKACEM, jour(3), null, null)));
        o.set("effectif_hors_periode_demande_dans_la_periode", valeur(declarer(admin(), P_CHIKHI, jour(3), null, null)));
        goldenNode("forfait_effectif_ou_demande", o);
    }

    @Test
    void parmi_plusieurs_prises_en_charge_couvrant_la_date_celle_avec_un_forfait_effectif_puis_la_plus_recente_gagne() throws Exception {
        supprimerLesPec(P_AMRANI);
        // deux PEC couvrant, la plus recente (anciennete 5) n'a qu'un forfait demande, l'ancienne un forfait effectif
        pec(P_AMRANI, CENTRE_A, "2020-01-01", null, null, null, null, F_HD, 1);
        pec(P_AMRANI, CENTRE_A, null, null, "2020-01-01", null, F_PREMIUM, null, 5);
        supprimerLesPec(P_BELKACEM);
        // deux PEC avec forfait effectif : la plus recente gagne
        pec(P_BELKACEM, CENTRE_A, "2020-01-01", null, null, null, null, F_HD, 1);
        pec(P_BELKACEM, CENTRE_A, "2020-01-01", null, null, null, null, F_PREMIUM, 5);
        supprimerLesPec(P_CHIKHI);
        pec(P_CHIKHI, CENTRE_A, "2020-01-01", null, null, null, null, F_PREMIUM, 5);
        pec(P_CHIKHI, CENTRE_A, "2020-01-01", null, null, null, null, F_HD, 1);
        ObjectNode o = mapper.createObjectNode();
        o.set("effectif_ancien_contre_demande_recent", valeur(declarer(admin(), P_AMRANI, jour(3), null, null)));
        o.set("deux_effectifs_le_plus_recent_premium", valeur(declarer(admin(), P_BELKACEM, jour(3), null, null)));
        o.set("deux_effectifs_le_plus_recent_premium_ordre_inverse", valeur(declarer(admin(), P_CHIKHI, jour(3), null, null)));
        goldenNode("plusieurs_prises_en_charge", o);
    }

    @Test
    void sans_prise_en_charge_couvrant_la_date_la_plus_recente_avec_un_forfait_est_prise_sans_condition_de_date() throws Exception {
        supprimerLesPec(P_AMRANI);
        pec(P_AMRANI, CENTRE_A, "2020-01-01", jour(100).toString(), null, null, null, F_HD, 1);
        supprimerLesPec(P_BELKACEM);
        pec(P_BELKACEM, CENTRE_A, jour(-10).toString(), null, null, null, null, F_PREMIUM, 1);
        supprimerLesPec(P_CHIKHI);
        // une PEC sans aucun forfait ne compte pas : repli sur l'autre
        pec(P_CHIKHI, CENTRE_A, "2020-01-01", jour(100).toString(), null, null, null, F_HD, 1);
        pec(P_CHIKHI, CENTRE_A, "2020-01-01", null, null, null, null, null, 9);
        supprimerLesPec(P_DAHMANI);
        pec(P_DAHMANI, CENTRE_A, "2020-01-01", null, null, null, null, null, 1);
        ObjectNode o = mapper.createObjectNode();
        o.set("pec_terminee_depuis_100_jours", valeur(declarer(admin(), P_AMRANI, jour(3), null, null)));
        o.set("pec_qui_commence_dans_10_jours", valeur(declarer(admin(), P_BELKACEM, jour(3), null, null)));
        o.set("pec_recente_sans_forfait", valeur(declarer(admin(), P_CHIKHI, jour(3), null, null)));
        o.set("seule_pec_sans_forfait", valeur(declarer(admin(), P_DAHMANI, jour(3), null, null)));
        goldenNode("repli_sans_condition_de_date", o);
    }

    @Test
    void les_bornes_de_la_periode_de_prise_en_charge_sont_incluses_et_un_forfait_d_un_autre_centre_est_ignore() throws Exception {
        supprimerLesPec(P_AMRANI);
        pec(P_AMRANI, CENTRE_A, jour(10).toString(), jour(5).toString(), null, null, null, F_PREMIUM, 3);
        pec(P_AMRANI, CENTRE_A, "2020-01-01", jour(11).toString(), null, null, null, F_HD, 1);
        supprimerLesPec(P_BELKACEM);
        pec(P_BELKACEM, CENTRE_A, "2020-01-01", null, null, null, null, F_CENTRE_B, 1);
        ObjectNode o = mapper.createObjectNode();
        o.set("veille_du_debut", valeur(declarer(admin(), P_AMRANI, jour(11), null, null)));
        o.set("premier_jour", valeur(declarer(admin(), P_AMRANI, jour(10), null, null)));
        o.set("dernier_jour", valeur(declarer(admin(), P_AMRANI, jour(5), null, null)));
        o.set("lendemain_de_la_fin_repli_sur_la_plus_recente", valeur(declarer(admin(), P_AMRANI, jour(4), null, null)));
        o.set("forfait_d_un_autre_centre", valeur(declarer(admin(), P_BELKACEM, jour(3), null, null)));
        goldenNode("bornes_de_prise_en_charge", o);
    }

    @Test
    void la_valeur_est_figee_apres_un_changement_de_forfait_de_prise_en_charge_et_de_tva_et_a_chaque_etape_du_cycle() throws Exception {
        Resp declaree = declarer(admin(), P_AMRANI, jour(10), null, null);
        UUID id = idCree(declaree);
        ObjectNode o = mapper.createObjectNode();
        o.set("a_la_declaration", valeur(declaree));
        // le monde change
        jdbc.update("UPDATE forfait SET prix = ? WHERE id = ?", new BigDecimal("20000.00"), F_HD);
        supprimerLaTva(CENTRE_A);
        tva(CENTRE_A, "9.00", false, "HEMODIALYSE", "2000-01-01", null);
        supprimerLesPec(P_AMRANI);
        pec(P_AMRANI, CENTRE_A, "2020-01-01", null, null, null, null, F_PREMIUM, 9);
        o.set("apres_qualification", valeur0(qualifier(admin(), id, "VOYAGE", null)));
        seance(P_AMRANI, CENTRE_A, jour(4), "VALIDEE");
        o.set("apres_rattrapage", valeur0(rattraper(admin(), id, jour(4))));
        o.set("apres_refus_d_annulation_d_une_rattrapee", valeur0(annuler(admin(), id, "erreur")));
        o.set("base_apres_tout", etat(CENTRE_A));
        // une nouvelle absence, meme patient, est valorisee avec le nouveau monde
        o.set("nouvelle_absence_valorisee_au_nouveau_monde", valeur(declarer(admin(), P_AMRANI, jour(9), null, null)));
        goldenNode("valeur_figee_nouveau_code", o);
    }

    private ObjectNode valeur0(Resp r) {
        ObjectNode o = mapper.createObjectNode();
        o.put("status", r.status());
        var c = json(r);
        o.put("statut", c.path("statut").asString(""));
        o.set("forfaitLibelle", c.path("forfaitLibelle"));
        o.set("montantHt", c.path("montantHt"));
        o.set("tauxTva", c.path("tauxTva"));
        o.set("prixTtc", c.path("prixTtc"));
        return o;
    }

    @Test
    void une_absence_posee_avant_le_passage_au_forfait_hors_taxe_garde_son_ancienne_valeur() throws Exception {
        // ancien calcul (forfait pris comme TTC) : 11900 TTC, 19 %, HT 10000 ; valeurs quelconques conservees telles quelles
        abs(1, P_AMRANI, jour(8)).valeur("Ancien forfait", "10000.00", "19.00", "8403.36").poser();
        UUID id = idFixture(1);
        ObjectNode o = mapper.createObjectNode();
        o.set("qualification", valeur0(qualifier(admin(), id, "MALADIE", null)));
        o.set("base", etat(CENTRE_A));
        goldenNode("ancienne_valorisation_conservee", o);
    }
}
