package com.hemodialyse.backend.characterization.absencespatients.detection;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Controle de nuit (cron 02:30) : {@code ControleDeNuitDesAbsences.controlerAbsences()} appele directement. Observe la table
 * {@code absence_patient}, le journal des notifications et les diffusions temps reel du centre.
 */
class ControleDeNuitDesAbsencesCharacterizationIntegrationTest extends DetectionAbsencesCharacterizationSupport {

    @Override
    protected String theme() {
        return "controle-de-nuit";
    }

    @Test
    void le_controle_de_nuit_cree_une_absence_a_qualifier_par_jour_des_sept_derniers_jours_sans_aujourdhui() {
        patientAttendu(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani");

        scheduler.controlerAbsences();

        ObjectNode o = objet();
        o.set("absences", lignes(CENTRE_A));
        goldenNode("sept_jours_hors_aujourdhui", o);
        assertEquals(7, compter(CENTRE_A));
    }

    @Test
    void une_absence_detectee_est_automatique_a_qualifier_sans_auteur_et_valorisee_au_forfait_du_patient() {
        patientAttendu(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani");
        // admis hier : une seule absence, toutes les colonnes sont observees
        jdbc.update("UPDATE patients SET date_admission = ? WHERE id = ?", java.sql.Date.valueOf(jour(1)), P_AMRANI);

        scheduler.controlerAbsences();

        ObjectNode o = objet();
        o.set("absences", etat(CENTRE_A));
        goldenNode("absence_detectee_toutes_colonnes", o);
    }

    @Test
    void le_controle_de_nuit_est_idempotent_et_ne_notifie_qu_une_fois_tant_que_l_alerte_n_est_pas_lue() {
        patientAttendu(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani");

        scheduler.controlerAbsences();
        var apresLePremier = lignes(CENTRE_A);
        var idsPremier = jdbc.queryForList("SELECT id FROM absence_patient WHERE center_id = ? ORDER BY date_seance", UUID.class, CENTRE_A);
        scheduler.controlerAbsences();

        assertEquals(apresLePremier, lignes(CENTRE_A));
        assertEquals(idsPremier, jdbc.queryForList("SELECT id FROM absence_patient WHERE center_id = ? ORDER BY date_seance", UUID.class, CENTRE_A));
        ObjectNode o = objet();
        o.set("absencesApresDeuxPassages", lignes(CENTRE_A));
        o.set("journal", journal(CENTRE_A));
        o.set("diffusions", diffusionsDuCentre(CENTRE_A));
        goldenNode("deux_passages_nouveau_code", o);
        assertEquals(1, journal(CENTRE_A).size(), "une seule notification ouverte par centre");
    }

    @Test
    void la_notification_compte_toutes_les_absences_a_qualifier_du_centre_et_celles_en_retard_apres_trois_jours() {
        patientAttendu(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani");
        // hors fenetre : une ancienne a qualifier (comptee : plus de limite de fenetre) et une deja qualifiee (non comptee)
        patientAttendu(P_BELKACEM, CENTRE_A, "ABS3-002", "Belkacem");
        jdbc.update("UPDATE patients SET en_sommeil = TRUE WHERE id = ?", P_BELKACEM);
        abs(1, P_BELKACEM, jour(30)).poser();
        abs(2, P_BELKACEM, jour(20)).statut("JUSTIFIEE").motif("MALADIE").poser();

        scheduler.controlerAbsences();

        ObjectNode o = objet();
        o.set("journal", journal(CENTRE_A));
        o.set("diffusions", diffusionsDuCentre(CENTRE_A));
        goldenNode("notification_a_qualifier_et_en_retard", o);
    }

    @Test
    void la_limite_du_retard_est_le_quatrieme_jour_avant_aujourdhui() {
        // a qualifier depuis J-3 : pas en retard ; depuis J-4 : en retard
        patientAttendu(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani");
        jdbc.update("UPDATE patients SET en_sommeil = TRUE WHERE id = ?", P_AMRANI);
        abs(1, P_AMRANI, jour(3)).poser();
        scheduler.controlerAbsences();
        ObjectNode o = objet();
        o.set("avecUneAbsenceDeJ-3", journal(CENTRE_A));
        abs(2, P_BELKACEM, jour(4)).poser();
        scheduler.controlerAbsences();
        o.set("apresAjoutDeJ-4", journal(CENTRE_A));
        goldenNode("limite_du_retard_nouveau_code", o);
    }

    @Test
    void aucune_notification_sans_absence_a_qualifier_meme_si_des_absences_qualifiees_existent() {
        patientAttendu(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani");
        jdbc.update("UPDATE patients SET en_sommeil = TRUE WHERE id = ?", P_AMRANI);
        abs(1, P_AMRANI, jour(2)).statut("NON_JUSTIFIEE").motif("NON_JUSTIFIEE").poser();

        scheduler.controlerAbsences();

        ObjectNode o = objet();
        o.set("journal", journal(CENTRE_A));
        o.set("diffusions", diffusionsDuCentre(CENTRE_A));
        o.set("absences", lignes(CENTRE_A));
        goldenNode("aucune_notification_sans_a_qualifier", o);
        assertTrue(journal(CENTRE_A).isEmpty());
    }

    @Test
    void chaque_centre_est_controle_avec_ses_patients_son_forfait_et_sa_notification() {
        patientAttendu(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani");
        patientAttendu(P_CENTRE_B, CENTRE_B, "ABS3-B01", "Zerrouki");
        jdbc.update("UPDATE patients SET date_admission = ? WHERE id = ?", java.sql.Date.valueOf(jour(2)), P_CENTRE_B);

        scheduler.controlerAbsences();

        ObjectNode o = objet();
        o.set("absencesCentreA", lignes(CENTRE_A));
        o.set("absencesCentreB", lignes(CENTRE_B));
        o.set("journalA", journal(CENTRE_A));
        o.set("journalB", journal(CENTRE_B));
        o.set("diffusionsA", diffusionsDuCentre(CENTRE_A));
        o.set("diffusionsB", diffusionsDuCentre(CENTRE_B));
        goldenNode("deux_centres", o);
    }

    @Test
    void un_centre_sans_patient_attendu_n_est_pas_notifie_mais_les_autres_le_sont() {
        patientAttendu(P_CENTRE_B, CENTRE_B, "ABS3-B01", "Zerrouki");

        scheduler.controlerAbsences();

        ObjectNode o = objet();
        o.set("journalA", journal(CENTRE_A));
        o.set("journalB", journal(CENTRE_B));
        o.put("absencesA", compter(CENTRE_A));
        o.put("absencesB", compter(CENTRE_B));
        goldenNode("centre_sans_patient", o);
    }

    @Test
    void un_centre_inactif_n_est_pas_controle() {
        patientAttendu(P_CENTRE_B, CENTRE_B, "ABS3-B01", "Zerrouki");
        jdbc.update("UPDATE centers SET actif = FALSE WHERE id = ?", CENTRE_B);

        scheduler.controlerAbsences();

        ObjectNode o = objet();
        o.put("absencesCentreInactif", compter(CENTRE_B));
        o.set("journalCentreInactif", journal(CENTRE_B));
        goldenNode("centre_inactif_nouveau_code", o);
    }

    @Test
    void un_centre_en_echec_n_empeche_ni_la_detection_ni_la_notification_des_autres() {
        patientAttendu(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani");
        patientAttendu(P_CENTRE_B, CENTRE_B, "ABS3-B01", "Zerrouki");
        // prix de forfait negatif : la valorisation du centre A est refusee par le domaine (exception non metier)
        jdbc.update("UPDATE forfait SET prix = -5.00 WHERE id = ?", F_HD);

        scheduler.controlerAbsences();

        ObjectNode o = objet();
        o.put("absencesA", compter(CENTRE_A));
        o.set("journalA", journal(CENTRE_A));
        o.set("absencesB", lignes(CENTRE_B));
        o.set("journalB", journal(CENTRE_B));
        goldenNode("centre_en_echec", o);
    }

    @Test
    void un_echec_n_arrete_que_l_absence_concernee_et_le_centre_est_quand_meme_notifie() {
        // le premier jour de la fenetre est traite sans erreur pour le patient AMRANI ; le second patient (id suivant) echoue
        patientAttendu(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani");
        patientAttendu(P_BELKACEM, CENTRE_A, "ABS3-002", "Belkacem");
        pecPrixNegatifPourBelkacem();

        scheduler.controlerAbsences();

        ObjectNode o = objet();
        o.set("absencesA", lignes(CENTRE_A));
        o.set("journalA", journal(CENTRE_A));
        goldenNode("echec_au_milieu_d_un_centre_nouveau_code", o);
    }

    private void pecPrixNegatifPourBelkacem() {
        UUID forfaitNegatif = nom("F_NEGATIF", 0xf0a9);
        forfait(forfaitNegatif, CENTRE_A, "F-NEG", "Forfait negatif", "-5.00");
        jdbc.update("UPDATE prise_en_charge SET forfait_effectif_id = ? WHERE patient_id = ?", forfaitNegatif, P_BELKACEM);
    }
}
