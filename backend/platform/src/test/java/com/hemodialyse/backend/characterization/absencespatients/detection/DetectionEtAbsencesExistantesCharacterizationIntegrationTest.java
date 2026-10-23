package com.hemodialyse.backend.characterization.absencespatients.detection;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.util.UUID;

/**
 * Detection face a une absence deja presente (annulee, qualifiee, rattrapee...), a une periode facturee (RG-ABS-024) et
 * valorisation figee des absences detectees.
 */
class DetectionEtAbsencesExistantesCharacterizationIntegrationTest extends DetectionAbsencesCharacterizationSupport {

    @Override
    protected String theme() {
        return "absences-existantes";
    }

    @Test
    void une_absence_deja_presente_meme_annulee_n_est_jamais_recreee_ni_modifiee_par_la_detection() {
        UUID[] p = {pid("X1", 1), pid("X2", 2), pid("X3", 3), pid("X4", 4), pid("X5", 5), pid("X6", 6)};
        for (int i = 0; i < p.length; i++) {
            pat(p[i], CENTRE_A, "ABS3-X" + i, "Existante" + i).poser();
        }
        abs(1, p[0], jour(3)).statut("ANNULEE").commentaire("Erreur de declaration").poser();
        abs(2, p[1], jour(3)).statut("JUSTIFIEE").motif("MALADIE").poser();
        abs(3, p[2], jour(3)).statut("NON_JUSTIFIEE").motif("NON_JUSTIFIEE").poser();
        abs(4, p[3], jour(3)).statut("RATTRAPEE").motif("MALADIE").rattrapee(jour(2)).poser();
        abs(5, p[4], jour(3)).poser();
        abs(6, p[5], jour(3)).declaree().poser();
        // les seances de rattrapage et du jour : sans effet sur la detection des autres jours
        scheduler.controlerAbsences();

        ObjectNode o = objet();
        for (int i = 0; i < p.length; i++) {
            o.set("patient" + (i + 1) + "_J-3_" + new String[]{"annulee", "justifiee", "non_justifiee", "rattrapee", "a_qualifier", "declaree"}[i],
                    datesDe(CENTRE_A, p[i]));
        }
        o.set("ligneDeJ-3", jdbcLignesDuJour());
        goldenNode("absence_presente", o);
    }

    private tools.jackson.databind.node.ArrayNode jdbcLignesDuJour() {
        tools.jackson.databind.node.ArrayNode a = mapper.createArrayNode();
        for (String l : lignesTexte()) if (l.startsWith(jour(3).toString())) a.add(l);
        return a;
    }

    private java.util.List<String> lignesTexte() {
        java.util.List<String> r = new java.util.ArrayList<>();
        lignes(CENTRE_A).forEach(n -> r.add(n.asString()));
        return r;
    }

    @Test
    void une_absence_d_un_autre_patient_le_meme_jour_ne_bloque_pas_la_detection() {
        pat(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani").admission(jour(2)).poser();
        pat(P_BELKACEM, CENTRE_A, "ABS3-002", "Belkacem").admission(jour(2)).poser();
        abs(1, P_AMRANI, jour(2)).statut("ANNULEE").commentaire("Erreur").poser();

        scheduler.controlerAbsences();

        ObjectNode o = objet();
        o.set("absences", lignes(CENTRE_A));
        goldenNode("autre_patient_meme_jour", o);
    }

    @Test
    void la_detection_n_ecrit_rien_dans_une_periode_facturee_bornes_incluses_pour_ce_patient_seulement() {
        pat(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani").poser();
        pat(P_BELKACEM, CENTRE_A, "ABS3-002", "Belkacem").poser();
        pat(P_CENTRE_B, CENTRE_B, "ABS3-B01", "Zerrouki").poser();
        facture(CENTRE_A, P_AMRANI, "F-ABS3-1", jour(5), jour(3));
        // facture du centre B pour un patient du centre B : aucun effet sur le centre A
        facture(CENTRE_B, P_CENTRE_B, "F-ABS3-B1", jour(7), jour(7));

        scheduler.controlerAbsences();

        ObjectNode o = objet();
        o.set("patientFactureDeJ-5AJ-3", datesDe(CENTRE_A, P_AMRANI));
        o.set("autrePatientDuCentre", datesDe(CENTRE_A, P_BELKACEM));
        o.set("patientDuCentreBFactureLe_J-7", datesDe(CENTRE_B, P_CENTRE_B));
        goldenNode("periode_facturee", o);
    }

    @Test
    void un_patient_dont_toute_la_fenetre_est_facturee_n_a_aucune_absence_ni_notification() {
        pat(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani").poser();
        facture(CENTRE_A, P_AMRANI, "F-ABS3-1", jour(30), jour(0));

        scheduler.controlerAbsences();

        ObjectNode o = objet();
        o.put("absences", compter(CENTRE_A));
        o.set("journal", journal(CENTRE_A));
        goldenNode("fenetre_toute_facturee", o);
    }

    @Test
    void la_valeur_d_une_absence_detectee_est_figee_au_forfait_et_au_taux_de_tva_du_jour_de_la_seance() {
        // taux : 19 % jusqu'a J-5 inclus, 9 % ensuite
        supprimerLaTva(CENTRE_A);
        tva(CENTRE_A, "19.00", false, "HEMODIALYSE", "2000-01-01", jour(5).toString());
        tva(CENTRE_A, "9.00", false, "HEMODIALYSE", jour(4).toString(), null);
        pat(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani").admission(jour(6)).poser();

        scheduler.controlerAbsences();

        ObjectNode o = objet();
        o.set("absences", lignes(CENTRE_A));
        goldenNode("taux_selon_la_date", o);
    }

    @Test
    void sans_prise_en_charge_sans_regle_de_tva_ou_avec_regle_exoneree_la_valeur_detectee_est_a_zero() {
        pat(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani").admission(jour(1)).sansPec().poser();
        pat(P_CENTRE_B, CENTRE_B, "ABS3-B01", "Zerrouki").admission(jour(1)).poser();
        supprimerLaTva(CENTRE_B);
        tva(CENTRE_B, "9.00", true, "HEMODIALYSE", "2000-01-01", null);

        scheduler.controlerAbsences();

        ObjectNode o = objet();
        o.set("sansPriseEnCharge", lignes(CENTRE_A));
        o.set("regleExoneree", lignes(CENTRE_B));
        supprimerLaTva(CENTRE_A);
        jdbc.update("DELETE FROM absence_patient WHERE center_id = ?", CENTRE_A);
        pat(P_BELKACEM, CENTRE_A, "ABS3-002", "Belkacem").admission(jour(1)).poser();
        scheduler.controlerAbsences();
        o.set("sansRegleDeTva", lignes(CENTRE_A));
        goldenNode("valeur_nulle_ou_exoneree", o);
    }

    @Test
    void la_valeur_d_une_absence_detectee_ne_change_pas_quand_le_forfait_change_apres_coup() {
        pat(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani").admission(jour(1)).poser();
        scheduler.controlerAbsences();
        jdbc.update("UPDATE forfait SET prix = 20000.00 WHERE id = ?", F_HD);
        scheduler.controlerAbsences();

        ObjectNode o = objet();
        o.set("absences", lignes(CENTRE_A));
        goldenNode("valeur_figee", o);
    }
}
