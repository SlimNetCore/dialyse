package com.hemodialyse.backend.characterization.planification.semaine;

import com.hemodialyse.backend.characterization.planification.SemaineCharacterizationSupport;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static com.hemodialyse.backend.domain.planning.model.JourSemaine.JEUDI;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.LUNDI;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.MARDI;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.MERCREDI;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.SAMEDI;

/**
 * Filet de caractérisation P6 : déplacements temporaires (table {@code deplacement_temporaire}, écrite par l'application d'une
 * optimisation, lue par la semaine) et séances réalisées (validées, signées, facturées) que le planning actuel ne montre plus.
 */
class DeplacementsEtSeancesRealiseesCharacterizationIntegrationTest extends SemaineCharacterizationSupport {

    @Override
    protected String theme() {
        return "p6-semaine/deplacements-et-seances";
    }

    // ------------------------------------------------------------------ déplacements temporaires

    @Test
    void un_deplacement_temporaire_deplace_le_patient_pour_cette_seule_date_avec_le_generateur_cible() throws Exception {
        decorDePlacement();
        UUID p = place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI, MERCREDI);
        deplacementTemporaire(CENTRE_A, p, semaine(1, LUNDI), salle2, apresMidi, g2a);
        arbre("deplacement", projectionDeLaSemaine(lireLaSemaine(1)));
    }

    @Test
    void la_reponse_complete_d_un_deplacement_temporaire_porte_le_drapeau_temporaire() throws Exception {
        petitDecor();
        UUID p = place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI);
        UUID s2 = salle(CENTRE_A, "SALLE_2", "Salle 2", "NON");
        UUID g2 = generateur(CENTRE_A, "G2A", s2, "EN_SERVICE");
        deplacementTemporaire(CENTRE_A, p, semaine(1, LUNDI), s2, matin, g2);
        lecture("deplacement_reponse_complete", lireLaSemaine(1));
    }

    @Test
    void un_deplacement_vers_une_salle_ou_un_creneau_inconnu_est_ignore_et_le_patient_reste_a_sa_place() throws Exception {
        decorDePlacement();
        UUID p1 = place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI);
        UUID p2 = place(CENTRE_A, "PAT_2", salle1, apresMidi, g1a, MARDI);
        deplacementTemporaire(CENTRE_A, p1, semaine(1, LUNDI), salleB, matin, g1b);
        deplacementTemporaire(CENTRE_A, p2, semaine(1, MARDI), salle2, creneauB, g2a);
        arbre("deplacement_cible_inconnue", projectionDeLaSemaine(lireLaSemaine(1)).get("cases"));
    }

    @Test
    void un_deplacement_vers_un_generateur_inconnu_ou_hors_service_garde_la_case_sans_code_de_generateur() throws Exception {
        decorDePlacement();
        UUID hs = generateur(CENTRE_A, "G_HS", salle2, "HORS_SERVICE");
        UUID p1 = place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI);
        UUID p2 = place(CENTRE_A, "PAT_2", salle1, matin, g1b, MARDI);
        deplacementTemporaire(CENTRE_A, p1, semaine(1, LUNDI), salle2, matin, UUID.nameUUIDFromBytes("inconnu".getBytes()));
        deplacementTemporaire(CENTRE_A, p2, semaine(1, MARDI), salle2, matin, hs);
        arbre("deplacement_generateur_inconnu", projectionDeLaSemaine(lireLaSemaine(1)));
    }

    @Test
    void un_deplacement_un_jour_ou_le_patient_n_est_pas_attendu_ou_hors_de_la_semaine_ou_d_un_autre_patient_est_sans_effet() throws Exception {
        decorDePlacement();
        UUID p = place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI);
        UUID sorti = place(CENTRE_A, "PAT_SORTI", salle1, matin, g1b, JEUDI);
        etat(sorti, "TRANSFERE", semaine(1, MARDI));
        nommerLaSemaine(2);
        deplacementTemporaire(CENTRE_A, p, semaine(1, MARDI), salle2, matin, g2a);
        deplacementTemporaire(CENTRE_A, p, semaine(2, LUNDI), salle2, matin, g2a);
        deplacementTemporaire(CENTRE_A, sorti, semaine(1, JEUDI), salle2, matin, g2a);
        deplacementTemporaire(CENTRE_B, p, semaine(1, LUNDI), salleB, creneauB, UUID.randomUUID());
        arbre("deplacement_sans_effet", projectionDeLaSemaine(lireLaSemaine(1)));
    }

    @Test
    void deux_patients_deplaces_sur_la_meme_case_la_surchargent_et_un_patient_a_risque_deplace_conserve_son_risque() throws Exception {
        decorDePlacement();
        UUID p1 = place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI);
        UUID p2 = place(CENTRE_A, "PAT_2", salle1, matin, g1b, LUNDI);
        serologie(CENTRE_A, p2, "AG_HBS", "POSITIF", "2026-03-01");
        deplacementTemporaire(CENTRE_A, p1, semaine(1, LUNDI), salle2, apresMidi, g2a);
        deplacementTemporaire(CENTRE_A, p2, semaine(1, LUNDI), salle2, apresMidi, g2a);
        arbre("deplacements_conflictuels", projectionDeLaSemaine(lireLaSemaine(1)));
    }

    @Test
    void un_deplacement_est_unique_par_patient_et_par_date_dans_la_base() {
        petitDecor();
        UUID p = place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI);
        deplacementTemporaire(CENTRE_A, p, semaine(1, LUNDI), salle1, matin, g1a);
        boolean refuse = false;
        try {
            deplacementTemporaire(CENTRE_A, p, semaine(1, LUNDI), salle1, matin, g1a);
        } catch (RuntimeException e) {
            refuse = true;
        }
        arbre("deplacement_unique", Map.of("deuxieme deplacement meme patient et meme date refuse par la base", refuse));
    }

    // ------------------------------------------------------------------ séances réalisées

    @Test
    void une_seance_realisee_hors_du_planning_actuel_s_affiche_la_ou_elle_a_eu_lieu_et_les_seances_suivantes_sont_signalees() throws Exception {
        decorDePlacement();
        UUID p = place(CENTRE_A, "PAT_1", salle1, matin, g1a, MERCREDI);
        seance(CENTRE_A, p, semaine(1, LUNDI), "VALIDEE", salle2, apresMidi, g2a);
        arbre("seance_hors_planning", projectionDeLaSemaine(lireLaSemaine(1)));
    }

    @Test
    void la_reponse_complete_porte_les_drapeaux_de_seance_realisee_et_de_seance_peut_etre_en_trop() throws Exception {
        petitDecor();
        UUID p = place(CENTRE_A, "PAT_1", salle1, matin, g1a, MERCREDI);
        seance(CENTRE_A, p, semaine(1, LUNDI), "FACTUREE", salle1, matin, g1a);
        lecture("seance_reponse_complete", lireLaSemaine(1));
    }

    @Test
    void seuls_les_statuts_valide_signe_et_facture_comptent_et_une_seance_prevue_du_planning_n_est_pas_dupliquee() throws Exception {
        decorDePlacement();
        generateur(CENTRE_A, "G1C", salle1, "EN_SERVICE");
        UUID p1 = place(CENTRE_A, "PAT_1", salle1, matin, g1a, MARDI);
        UUID p2 = place(CENTRE_A, "PAT_2", salle1, matin, g1b, MARDI);
        UUID p3 = place(CENTRE_A, "PAT_3", salle2, matin, g2a, MARDI);
        UUID p4 = place(CENTRE_A, "PAT_4", salle2, matin, null, MARDI);
        UUID p5 = place(CENTRE_A, "PAT_5", salle2, matin, null, MARDI);
        UUID p6 = place(CENTRE_A, "PAT_6", salle2, matin, null, MARDI);
        seance(CENTRE_A, p1, semaine(1, LUNDI), "CREE", salle1, matin, g1a);
        seance(CENTRE_A, p2, semaine(1, LUNDI), "VALIDEE", salle1, matin, g1b);
        seance(CENTRE_A, p3, semaine(1, LUNDI), "SIGNEE", salle2, matin, g2a);
        seance(CENTRE_A, p4, semaine(1, LUNDI), "ANNULEE", salle2, matin, null);
        seance(CENTRE_A, p5, semaine(1, MARDI), "VALIDEE", salle2, matin, null);
        seance(CENTRE_A, p6, semaine(1, LUNDI), "FACTUREE", salle2, matin, null);
        arbre("statuts_et_doublons", projectionDeLaSemaine(lireLaSemaine(1)));
    }

    @Test
    void une_seance_sans_place_memorisee_est_placee_sur_la_place_actuelle_du_patient() throws Exception {
        decorDePlacement();
        UUID p = place(CENTRE_A, "PAT_1", salle2, apresMidi, g2a, MERCREDI);
        seance(CENTRE_A, p, semaine(1, LUNDI), "VALIDEE", null, null, null);
        arbre("place_non_memorisee", projectionDeLaSemaine(lireLaSemaine(1)));
    }

    @Test
    void une_seance_sans_place_memorisee_d_un_patient_desormais_non_place_ou_dont_la_salle_a_disparu_est_sans_case() throws Exception {
        decorDePlacement();
        UUID p1 = patientPlace(CENTRE_A, "PAT_NON_PLACE", salle1, matin, MERCREDI);
        jdbc.update("UPDATE patients SET salle_id = NULL, position_id = NULL WHERE id = ?", p1);
        UUID p2 = place(CENTRE_A, "PAT_SALLE_DISPARUE", salle1, matin, g1a, MERCREDI);
        UUID disparue = salle(CENTRE_A, "SALLE_DISPARUE", "Salle disparue", "NON");
        seance(CENTRE_A, p1, semaine(1, LUNDI), "VALIDEE", null, null, null);
        seance(CENTRE_A, p2, semaine(1, MARDI), "SIGNEE", disparue, matin, g1a);
        jdbc.update("DELETE FROM salle WHERE id = ?", disparue);
        arbre("seance_sans_case", projectionDeLaSemaine(lireLaSemaine(1)));
    }

    @Test
    void une_seance_d_un_patient_sorti_conserve_son_nom_et_les_seances_hors_semaine_ou_d_un_autre_centre_sont_ignorees() throws Exception {
        decorDePlacement();
        UUID sorti = place(CENTRE_A, "PAT_SORTI", salle1, matin, g1a, MARDI);
        etat(sorti, "TRANSFERE", semaine(0, MARDI));
        UUID autre = patientPlace(CENTRE_B, "PAT_AUTRE", salleB, creneauB, MARDI);
        nommerLaSemaine(2);
        seance(CENTRE_A, sorti, semaine(1, MARDI), "VALIDEE", salle1, matin, g1a);
        seance(CENTRE_A, sorti, semaine(0, SAMEDI), "VALIDEE", salle1, matin, g1a);
        seance(CENTRE_A, sorti, semaine(2, LUNDI), "VALIDEE", salle1, matin, g1a);
        seance(CENTRE_B, autre, semaine(1, MARDI), "VALIDEE", salleB, creneauB, null);
        arbre("seances_perimetre", projectionDeLaSemaine(lireLaSemaine(1)));
    }

    @Test
    void une_seance_realisee_ne_cree_aucun_conflit_et_ne_modifie_pas_le_compte_des_patients_a_replanifier() throws Exception {
        petitDecor();
        joursOuvertsDuCentre(CENTRE_A, "LUNDI,MARDI");
        UUID p = place(CENTRE_A, "PAT_1", salle1, matin, g1a, MARDI);
        UUID q = place(CENTRE_A, "PAT_2", salle1, matin, g1a, MARDI);
        seance(CENTRE_A, p, semaine(1, MERCREDI), "VALIDEE", salle1, matin, g1a);
        seance(CENTRE_A, q, semaine(1, SAMEDI), "VALIDEE", salle1, matin, g1a);
        Map<String, Object> m = new LinkedHashMap<>(projectionDeLaSemaine(lireLaSemaine(1)));
        arbre("seance_sans_conflit", m);
    }

    @Test
    void la_premiere_seance_hors_planning_fixe_la_date_de_reference_de_la_verification_des_suivantes() throws Exception {
        petitDecor();
        UUID p = place(CENTRE_A, "PAT_1", salle1, matin, g1a, MERCREDI, JEUDI, SAMEDI);
        seance(CENTRE_A, p, semaine(1, MARDI), "VALIDEE", salle1, matin, g1a);
        seance(CENTRE_A, p, semaine(1, LUNDI), "VALIDEE", salle1, matin, g1a);
        seance(CENTRE_A, p, semaine(1, JEUDI), "VALIDEE", salle1, matin, g1a);
        arbre("date_de_reference", projectionDeLaSemaine(lireLaSemaine(1)).get("cases"));
    }
}
