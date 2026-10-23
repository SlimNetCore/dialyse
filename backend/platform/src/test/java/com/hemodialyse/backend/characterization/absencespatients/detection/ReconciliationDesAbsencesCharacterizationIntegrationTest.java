package com.hemodialyse.backend.characterization.absencespatients.detection;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reconciliation : une absence « a qualifier » dont la seance a ete realisee ce jour-la est annulee (commentaire impose, sans
 * auteur). Declenchee par le controle de nuit (J-7..J-1) et par {@code POST /rattrapage-detection}. Les patients des scenarios
 * n'ont aucun jour coche : la detection ne cree rien, seule la reconciliation agit.
 */
class ReconciliationDesAbsencesCharacterizationIntegrationTest extends DetectionAbsencesCharacterizationSupport {

    @Override
    protected String theme() {
        return "reconciliation";
    }

    private void patientsSansJour(UUID... ids) {
        int i = 1;
        for (UUID id : ids) {
            pat(id, CENTRE_A, "ABS3-R" + i, "Patient" + i).aucunJour().poser();
            i++;
        }
    }

    @Test
    void une_absence_a_qualifier_dont_la_seance_est_realisee_est_annulee_avec_le_commentaire_impose_et_sans_auteur() {
        patientsSansJour(P_AMRANI);
        abs(1, P_AMRANI, jour(2)).poser();
        seance(P_AMRANI, CENTRE_A, jour(2), "VALIDEE");

        scheduler.controlerAbsences();

        ObjectNode o = objet();
        o.set("absences", etat(CENTRE_A));
        goldenNode("annulation_toutes_colonnes", o);
    }

    @Test
    void seules_les_seances_validees_signees_ou_facturees_reconcilient_l_absence() {
        UUID[] p = {pid("R1", 1), pid("R2", 2), pid("R3", 3), pid("R4", 4), pid("R5", 5), pid("R6", 6)};
        String[] statuts = {"CREE", "ABSENT", "BROUILLON", "VALIDEE", "SIGNEE", "FACTUREE"};
        patientsSansJour(p);
        for (int i = 0; i < p.length; i++) {
            abs(i + 1, p[i], jour(2)).poser();
            seance(p[i], CENTRE_A, jour(2), statuts[i]);
        }

        scheduler.controlerAbsences();

        ObjectNode o = objet();
        o.set("absences", lignes(CENTRE_A));
        goldenNode("statuts_de_seance", o);
    }

    @Test
    void une_absence_deja_qualifiee_rattrapee_ou_annulee_n_est_pas_touchee_meme_avec_une_seance_realisee() {
        UUID[] p = {pid("R1", 1), pid("R2", 2), pid("R3", 3), pid("R4", 4)};
        patientsSansJour(p);
        abs(1, p[0], jour(2)).statut("JUSTIFIEE").motif("MALADIE").poser();
        abs(2, p[1], jour(2)).statut("NON_JUSTIFIEE").motif("NON_JUSTIFIEE").commentaire("sans nouvelles").poser();
        abs(3, p[2], jour(3)).statut("RATTRAPEE").motif("MALADIE").rattrapee(jour(1)).poser();
        abs(4, p[3], jour(2)).statut("ANNULEE").commentaire("Declaration erronee").poser();
        for (int i = 0; i < p.length; i++) seance(p[i], CENTRE_A, i == 2 ? jour(3) : jour(2), "VALIDEE");

        scheduler.controlerAbsences();

        ObjectNode o = objet();
        o.set("absences", lignes(CENTRE_A));
        goldenNode("absences_non_a_qualifier_intactes", o);
    }

    @Test
    void la_reconciliation_ne_touche_pas_une_absence_declaree_par_un_utilisateur_sans_motif() {
        patientsSansJour(P_AMRANI);
        abs(1, P_AMRANI, jour(2)).declaree().poser();
        seance(P_AMRANI, CENTRE_A, jour(2), "VALIDEE");

        scheduler.controlerAbsences();

        ObjectNode o = objet();
        o.set("absences", lignes(CENTRE_A));
        goldenNode("absence_declaree_sans_motif_conservee_nouveau_code", o);
    }

    @Test
    void la_reconciliation_ignore_la_periode_facturee_et_garde_le_commentaire() {
        patientsSansJour(P_AMRANI);
        abs(1, P_AMRANI, jour(2)).commentaire("Patient joint par telephone").poser();
        seance(P_AMRANI, CENTRE_A, jour(2), "SIGNEE");
        facture(CENTRE_A, P_AMRANI, "F-ABS3-1", jour(5), jour(1));

        scheduler.controlerAbsences();

        ObjectNode o = objet();
        o.set("absences", lignes(CENTRE_A));
        goldenNode("periode_facturee_ignoree_nouveau_code", o);
    }

    @Test
    void la_seance_d_un_autre_patient_ou_d_un_autre_jour_ou_d_un_autre_centre_ne_reconcilie_pas() {
        patientsSansJour(P_AMRANI, P_BELKACEM);
        pat(P_CENTRE_B, CENTRE_B, "ABS3-B01", "Zerrouki").aucunJour().poser();
        abs(1, P_AMRANI, jour(2)).poser();
        abs(2, P_CENTRE_B, jour(2)).centre(CENTRE_B).valeur("Forfait du centre B", "1090.00", "9.00", "1000.00").poser();
        seance(P_BELKACEM, CENTRE_A, jour(2), "VALIDEE");
        seance(P_AMRANI, CENTRE_A, jour(3), "VALIDEE");
        seance(P_CENTRE_B, CENTRE_B, jour(3), "VALIDEE");

        scheduler.controlerAbsences();

        ObjectNode o = objet();
        o.set("centreA", lignes(CENTRE_A));
        o.set("centreB", lignes(CENTRE_B));
        goldenNode("seance_non_correspondante", o);
    }

    @Test
    void le_controle_de_nuit_ne_reconcilie_que_de_j_7_a_j_1_et_le_rattrapage_de_detection_atteint_les_jours_anciens() throws Exception {
        UUID[] p = {pid("R1", 1), pid("R2", 2), pid("R3", 3), pid("R4", 4), pid("R5", 5)};
        patientsSansJour(p);
        int[] jours = {0, 1, 7, 8, 40};
        for (int i = 0; i < p.length; i++) {
            abs(i + 1, p[i], jour(jours[i])).poser();
            seance(p[i], CENTRE_A, jour(jours[i]), "VALIDEE");
        }

        scheduler.controlerAbsences();

        ObjectNode o = objet();
        o.set("apresLeControleDeNuit_J_J-1_J-7_J-8_J-40", lignes(CENTRE_A));
        var reponse = rattraper(jour(45), jour(1));
        o.set("reponseDuRattrapage", resume(reponse));
        o.set("apresLeRattrapageDeDetection", lignes(CENTRE_A));
        goldenNode("fenetres_de_reconciliation", o);
    }

    @Test
    void la_reconciliation_precede_la_detection_et_l_absence_annulee_occupe_le_jour() {
        pat(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani").poser();
        abs(1, P_AMRANI, jour(2)).poser();
        seance(P_AMRANI, CENTRE_A, jour(2), "VALIDEE");

        scheduler.controlerAbsences();

        ObjectNode o = objet();
        o.set("absences", lignes(CENTRE_A));
        o.set("journal", journal(CENTRE_A));
        goldenNode("reconciliation_avant_detection", o);
        assertEquals(7, compter(CENTRE_A));
    }

    @Test
    void la_notification_compte_les_absences_apres_reconciliation() {
        patientsSansJour(P_AMRANI, P_BELKACEM);
        abs(1, P_AMRANI, jour(2)).poser();
        abs(2, P_BELKACEM, jour(6)).poser();
        seance(P_AMRANI, CENTRE_A, jour(2), "VALIDEE");

        scheduler.controlerAbsences();

        ObjectNode o = objet();
        o.set("journal", journal(CENTRE_A));
        goldenNode("notification_apres_reconciliation", o);
    }

    @Test
    void le_rattrapage_de_detection_rapporte_le_nombre_d_absences_annulees_et_creees() throws Exception {
        pat(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani").poser();
        patientsSansJour(P_BELKACEM);
        abs(1, P_BELKACEM, jour(10)).poser();
        abs(2, P_BELKACEM, jour(9)).poser();
        seance(P_BELKACEM, CENTRE_A, jour(10), "FACTUREE");
        seance(P_BELKACEM, CENTRE_A, jour(9), "VALIDEE");

        var r = rattraper(jour(10), jour(9));

        ObjectNode o = objet();
        o.set("reponse", resume(r));
        o.set("absences", lignes(CENTRE_A));
        goldenNode("rattrapage_annulees_et_creees", o);
    }
}
