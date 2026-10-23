package com.hemodialyse.backend.characterization.absencespatients.detection;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Requete « patient attendu » (jour coche, admission, sommeil, etat et fin de sejour, jours feries, fermetures, seance
 * realisee), observee par les absences que le controle de nuit cree sur la fenetre J-7..J-1.
 */
class PatientAttenduCharacterizationIntegrationTest extends DetectionAbsencesCharacterizationSupport {

    @Override
    protected String theme() {
        return "patient-attendu";
    }

    @Test
    void un_patient_n_est_attendu_que_les_jours_de_la_semaine_coches() {
        pat(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani").seulementLeJourDe(jour(3)).poser();
        pat(P_BELKACEM, CENTRE_A, "ABS3-002", "Belkacem").seulementLeJourDe(jour(5)).poser();
        pat(P_CHIKHI, CENTRE_A, "ABS3-003", "Chikhi").aucunJour().poser();

        scheduler.controlerAbsences();

        ObjectNode o = objet();
        o.set("absences", lignes(CENTRE_A));
        goldenNode("jours_coches", o);
        assertEquals(2, compter(CENTRE_A));
    }

    @Test
    void un_patient_dont_les_jours_ne_sont_pas_renseignes_n_est_jamais_attendu() {
        pat(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani").poser();
        jdbc.update("UPDATE patients SET jour_lundi = NULL, jour_mardi = NULL, jour_mercredi = NULL, jour_jeudi = NULL, "
                + "jour_vendredi = NULL, jour_samedi = NULL, jour_dimanche = NULL WHERE id = ?", P_AMRANI);

        scheduler.controlerAbsences();

        assertEquals(0, compter(CENTRE_A));
    }

    @Test
    void un_patient_en_sommeil_n_est_pas_attendu_et_un_sommeil_non_renseigne_equivaut_a_actif() {
        pat(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani").sommeil(Boolean.TRUE).poser();
        pat(P_BELKACEM, CENTRE_A, "ABS3-002", "Belkacem").sommeil(null).poser();
        pat(P_CHIKHI, CENTRE_A, "ABS3-003", "Chikhi").sommeil(Boolean.FALSE).poser();

        scheduler.controlerAbsences();

        ObjectNode o = objet();
        o.set("enSommeil", datesDe(CENTRE_A, P_AMRANI));
        o.set("sommeilNonRenseigne", datesDe(CENTRE_A, P_BELKACEM));
        o.set("actif", datesDe(CENTRE_A, P_CHIKHI));
        goldenNode("sommeil", o);
    }

    @Test
    void un_patient_est_attendu_a_partir_du_jour_de_son_admission_inclus() {
        pat(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani").admission(jour(3)).poser();
        pat(P_BELKACEM, CENTRE_A, "ABS3-002", "Belkacem").admission(jour(1)).poser();
        pat(P_CHIKHI, CENTRE_A, "ABS3-003", "Chikhi").admission(jour(0)).poser();
        pat(P_DAHMANI, CENTRE_A, "ABS3-004", "Dahmani").admission(jour(-2)).poser();

        scheduler.controlerAbsences();

        ObjectNode o = objet();
        o.set("admisIlYATroisJours", datesDe(CENTRE_A, P_AMRANI));
        o.set("admisHier", datesDe(CENTRE_A, P_BELKACEM));
        o.set("admisAujourdhui", datesDe(CENTRE_A, P_CHIKHI));
        o.set("admisDansDeuxJours", datesDe(CENTRE_A, P_DAHMANI));
        goldenNode("admission", o);
    }

    @Test
    void l_etat_du_patient_et_la_date_de_l_evenement_decident_jusqu_a_quel_jour_il_est_attendu() {
        LocalDate evt = jour(4);
        Map<String, UUID> patients = new LinkedHashMap<>();
        int rang = 1;
        String[][] cas = {
                {"TRANSFERE", "avec"}, {"GUERRI", "avec"}, {"DECEDE", "avec"}, {"GREFFE", "avec"},
                {"TRANSFERE", "sans"}, {"GUERRI", "sans"}, {"DECEDE", "sans"}, {"GREFFE", "sans"},
                {"OCCASIONNEL", "avec"}, {"VACANCIER_LOCAL", "avec"}, {"VACANCIER_ETRANGER", "avec"},
                {"OCCASIONNEL", "sans"}, {"VACANCIER_LOCAL", "sans"}, {"VACANCIER_ETRANGER", "sans"},
                {"PERMANENT", "avec"}, {"PERMANENT", "sans"}, {"AUTRE_ETAT", "avec"}, {"NUL", "sans"}, {"NUL", "avec"}};
        for (String[] c : cas) {
            String cle = c[0] + "_" + c[1] + "_date_d_evenement";
            UUID id = pid("ETAT_" + c[0] + "_" + c[1], rang);
            var p = pat(id, CENTRE_A, "ABS3-E" + rang, "Etat" + rang);
            LocalDate d = c[1].equals("avec") ? evt : null;
            if (c[0].equals("NUL")) {
                p.etatNul();
            } else {
                p.etat(c[0], d);
            }
            p.poser();
            if (c[0].equals("NUL") && d != null) {
                jdbc.update("UPDATE patients SET date_evenement_etat = ? WHERE id = ?", java.sql.Date.valueOf(d), id);
            }
            patients.put(cle, id);
            rang++;
        }

        scheduler.controlerAbsences();

        // l'evenement tombe a <J-4> : date de l'absence la plus ancienne / la plus recente par patient
        ObjectNode o = objet();
        patients.forEach((cle, id) -> o.set(cle, datesDe(CENTRE_A, id)));
        goldenNode("etat_et_date_d_evenement_nouveau_code", o);
    }

    @Test
    void un_jour_ferie_ou_de_fermeture_du_centre_n_attend_aucun_patient_et_seul_le_centre_concerne_est_touche() {
        pat(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani").poser();
        pat(P_CENTRE_B, CENTRE_B, "ABS3-B01", "Zerrouki").poser();
        ferie(CENTRE_A, jour(3));
        fermeture(CENTRE_A, jour(5));
        ferie(CENTRE_A, jour(6));
        fermeture(CENTRE_A, jour(6));
        ferie(CENTRE_B, jour(2));
        // un ferie hors fenetre ou dans le futur ne change rien
        ferie(CENTRE_A, jour(30));
        fermeture(CENTRE_A, jour(-3));

        scheduler.controlerAbsences();

        ObjectNode o = objet();
        o.set("centreA", datesDe(CENTRE_A, P_AMRANI));
        o.set("centreB", datesDe(CENTRE_B, P_CENTRE_B));
        goldenNode("feries_et_fermetures", o);
    }

    @Test
    void une_seance_realisee_le_jour_meme_retire_le_patient_des_attendus_selon_son_statut() {
        pat(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani").poser();
        patientAttendu(P_BELKACEM, CENTRE_A, "ABS3-002", "Belkacem");
        jdbc.update("UPDATE patients SET en_sommeil = TRUE WHERE id = ?", P_BELKACEM);
        String[] statuts = {"CREE", "ABSENT", "BROUILLON", "VALIDEE", "SIGNEE", "FACTUREE"};
        for (int i = 0; i < statuts.length; i++) {
            seance(P_AMRANI, CENTRE_A, jour(i + 1), statuts[i]);
        }
        // la seance d'un autre patient le meme jour n'efface pas l'attente d'AMRANI
        seance(P_BELKACEM, CENTRE_A, jour(7), "VALIDEE");

        scheduler.controlerAbsences();

        ObjectNode o = objet();
        o.set("CREE_J-1_ABSENT_J-2_BROUILLON_J-3_VALIDEE_J-4_SIGNEE_J-5_FACTUREE_J-6_AUCUNE_J-7", datesDe(CENTRE_A, P_AMRANI));
        goldenNode("seance_realisee_par_statut", o);
    }

    @Test
    void la_seance_realisee_d_un_autre_centre_ne_compte_pas_pour_le_centre_controle() {
        pat(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani").poser();
        pat(P_CENTRE_B, CENTRE_B, "ABS3-B01", "Zerrouki").poser();
        seance(P_CENTRE_B, CENTRE_B, jour(1), "VALIDEE");

        scheduler.controlerAbsences();

        ObjectNode o = objet();
        o.set("centreA", datesDe(CENTRE_A, P_AMRANI));
        o.set("centreB", datesDe(CENTRE_B, P_CENTRE_B));
        goldenNode("seance_realisee_autre_centre", o);
    }

    @Test
    void un_patient_attendu_dont_la_seance_est_realisee_tous_les_jours_n_a_aucune_absence_et_pas_de_notification() {
        pat(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani").poser();
        for (int i = 1; i <= 7; i++) seance(P_AMRANI, CENTRE_A, jour(i), "VALIDEE");

        scheduler.controlerAbsences();

        assertEquals(0, compter(CENTRE_A));
        assertEquals(0, journal(CENTRE_A).size());
    }
}
