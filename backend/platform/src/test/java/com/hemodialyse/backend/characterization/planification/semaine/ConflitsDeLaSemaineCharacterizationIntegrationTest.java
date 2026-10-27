package com.hemodialyse.backend.characterization.planification.semaine;

import com.hemodialyse.backend.characterization.planification.SemaineCharacterizationSupport;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.hemodialyse.backend.domain.planning.model.JourSemaine.LUNDI;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.MARDI;

/**
 * Filet de caractérisation P6 : les six conflits de la semaine (générateur réservé deux fois, salle surchargée, générateur
 * indisponible, patient sans générateur, isolement non respecté, générateur mélangeant patients à risque et sans risque).
 */
class ConflitsDeLaSemaineCharacterizationIntegrationTest extends SemaineCharacterizationSupport {

    @Override
    protected String theme() {
        return "p6-semaine/conflits";
    }

    @Test
    void un_generateur_reserve_deux_fois_dans_la_meme_case_est_un_conflit_et_la_salle_est_surchargee_si_elle_est_pleine() throws Exception {
        decorDePlacement();
        place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI);
        place(CENTRE_A, "PAT_2", salle1, matin, g1a, LUNDI);
        place(CENTRE_A, "PAT_3", salle1, matin, g1b, LUNDI);
        arbre("generateur_double", projectionDeLaSemaine(lireLaSemaine(1)).get("conflits"));
    }

    @Test
    void une_salle_surchargee_cite_les_patients_tries_par_nom_et_la_capacite_est_celle_des_generateurs_en_service() throws Exception {
        decorDePlacement();
        place(CENTRE_A, "PAT_C", salle2, matin, g2a, LUNDI);
        place(CENTRE_A, "PAT_A", salle2, matin, null, LUNDI);
        place(CENTRE_A, "PAT_B", salle2, matin, null, LUNDI);
        arbre("salle_surchargee", projectionDeLaSemaine(lireLaSemaine(1)).get("conflits"));
    }

    @Test
    void un_patient_sans_generateur_est_signale_et_compte_dans_la_surcharge_d_une_salle_sans_generateur() throws Exception {
        petitDecor();
        UUID salleVide = salle(CENTRE_A, "SALLE_SANS_GENERATEUR", "Salle sans generateur", "NON");
        place(CENTRE_A, "PAT_SANS_GENERATEUR", salle1, matin, null, LUNDI);
        place(CENTRE_A, "PAT_SALLE_VIDE", salleVide, matin, null, MARDI);
        arbre("sans_generateur", projectionDeLaSemaine(lireLaSemaine(1)));
    }

    @Test
    void un_generateur_hors_service_ou_en_maintenance_ou_d_une_autre_salle_ou_inconnu_est_indisponible() throws Exception {
        decorDePlacement();
        UUID hs = generateur(CENTRE_A, "G_HS", salle1, "HORS_SERVICE");
        UUID maintenance = generateur(CENTRE_A, "G_MAINT", salle1, "EN_MAINTENANCE");
        UUID reforme = generateur(CENTRE_A, "G_REFORME", salle1, "REFORME");
        place(CENTRE_A, "PAT_HS", salle1, matin, hs, LUNDI);
        place(CENTRE_A, "PAT_MAINT", salle1, matin, maintenance, MARDI);
        place(CENTRE_A, "PAT_REFORME", salle1, apresMidi, reforme, LUNDI);
        place(CENTRE_A, "PAT_AUTRE_SALLE", salle1, apresMidi, g2a, MARDI);
        place(CENTRE_A, "PAT_INCONNU", salle2, matin, UUID.nameUUIDFromBytes("generateur-inexistant".getBytes()), LUNDI);
        arbre("generateur_indisponible", projectionDeLaSemaine(lireLaSemaine(1)));
    }

    @Test
    void un_patient_a_risque_hors_salle_d_isolement_ou_un_patient_sain_en_isolement_est_un_conflit() throws Exception {
        decorDePlacement();
        UUID risque = place(CENTRE_A, "PAT_RISQUE_EN_SALLE_1", salle1, matin, g1a, LUNDI);
        UUID sain = place(CENTRE_A, "PAT_SAIN_EN_ISOLEMENT", salleIsolement, matin, giso, LUNDI);
        UUID conforme = place(CENTRE_A, "PAT_RISQUE_EN_ISOLEMENT", salleIsolement, apresMidi, giso, LUNDI);
        serologie(CENTRE_A, risque, "VIH_AC", "POSITIF", "2026-03-01");
        serologie(CENTRE_A, conforme, "ARN_VHC", "POSITIF", "2026-03-01");
        arbre("isolement_non_respecte", projectionDeLaSemaine(lireLaSemaine(1)));
    }

    @Test
    void sans_salle_d_isolement_declaree_un_patient_sain_n_est_jamais_un_conflit_d_isolement() throws Exception {
        decorDePlacement();
        jdbc.update("UPDATE salle SET isolement = 'NON' WHERE center_id = ?", CENTRE_A);
        UUID risque = place(CENTRE_A, "PAT_RISQUE", salle1, matin, g1a, LUNDI);
        place(CENTRE_A, "PAT_SAIN", salle2, matin, g2a, LUNDI);
        serologie(CENTRE_A, risque, "AG_HBS", "POSITIF", "2026-03-01");
        arbre("sans_salle_d_isolement", projectionDeLaSemaine(lireLaSemaine(1)).get("conflits"));
    }

    @Test
    void un_generateur_qui_sert_des_patients_a_risque_et_sans_risque_est_un_conflit_mixte_sans_case() throws Exception {
        decorDePlacement();
        UUID risque = place(CENTRE_A, "PAT_RISQUE", salleIsolement, matin, giso, LUNDI);
        place(CENTRE_A, "PAT_SAIN", salleIsolement, apresMidi, giso, MARDI);
        serologie(CENTRE_A, risque, "AG_HBS", "POSITIF", "2026-03-01");
        arbre("generateur_mixte", projectionDeLaSemaine(lireLaSemaine(1)).get("conflits"));
    }

    @Test
    void les_conflits_sont_lus_sur_la_semaine_demandee_meme_pour_un_patient_sorti_en_cours_de_semaine() throws Exception {
        petitDecor();
        UUID a = place(CENTRE_A, "PAT_A", salle1, matin, g1a, LUNDI, MARDI);
        place(CENTRE_A, "PAT_B", salle1, matin, g1a, LUNDI, MARDI);
        etat(a, "TRANSFERE", semaine(1, LUNDI));
        arbre("conflit_puis_sortie", projectionDeLaSemaine(lireLaSemaine(1)));
    }

    @Test
    void un_patient_sorti_avant_la_semaine_ne_cree_plus_de_conflit_de_generateur_mixte() throws Exception {
        decorDePlacement();
        UUID risque = place(CENTRE_A, "PAT_RISQUE", salleIsolement, matin, giso, LUNDI);
        UUID sain = place(CENTRE_A, "PAT_SAIN", salleIsolement, matin, giso, MARDI);
        serologie(CENTRE_A, risque, "ARN_VHC", "POSITIF", "2026-03-01");
        etat(sain, "TRANSFERE", semaine(0, MARDI));
        arbre("mixte_apres_sortie", projectionDeLaSemaine(lireLaSemaine(1)).get("conflits"));
    }
}
