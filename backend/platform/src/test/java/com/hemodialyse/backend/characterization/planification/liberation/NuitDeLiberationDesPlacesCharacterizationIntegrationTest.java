package com.hemodialyse.backend.characterization.planification.liberation;

import com.hemodialyse.backend.characterization.planification.LiberationCharacterizationSupport;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Filet de caractérisation P7 : le job de 04:00 ({@code LiberationPlacesScheduler.libererPlaces()}, appelé directement) libère la place
 * (salle, créneau, générateur, jours de dialyse) des patients dont le dernier jour d'occupation est dépassé et écrit le mouvement
 * {@code PLACE_LIBEREE} ({@code FinOccupation}, {@code MouvementsPatient}, {@code Patient.libererPlacement}). Chaque golden montre l'état des
 * patients (colonnes de place) et les mouvements après le passage du job ; les dates sont des alias relatifs à aujourd'hui.
 */
class NuitDeLiberationDesPlacesCharacterizationIntegrationTest extends LiberationCharacterizationSupport {

    @Override
    protected String theme() {
        return "p7-liberation/nuit";
    }

    private void passerLaNuit(String scenario) {
        scheduler.libererPlaces();
        etatDesPatients(scenario + "_patients");
        etatDesMouvements(scenario + "_mouvements");
    }

    // ------------------------------------------------------------------ états de sortie avec date d'évènement

    @Test
    void un_transfere_ou_gueri_est_libere_le_lendemain_de_sa_date_d_evenement() {
        decorDePlacement();
        nommerLesJours();
        sorti(CENTRE_A, "TRANSFERE_J-2", "TRANSFERE", jour(-2));
        sorti(CENTRE_A, "TRANSFERE_J-1", "TRANSFERE", jour(-1));
        sorti(CENTRE_A, "TRANSFERE_J", "TRANSFERE", jour(0));
        sorti(CENTRE_A, "TRANSFERE_J+1", "TRANSFERE", jour(1));
        sorti(CENTRE_A, "GUERRI_J-1", "GUERRI", jour(-1));
        sorti(CENTRE_A, "GUERRI_J", "GUERRI", jour(0));
        sorti(CENTRE_A, "GUERRI_J+3", "GUERRI", jour(3));
        passerLaNuit("transfere_gueri");
    }

    @Test
    void un_decede_ou_greffe_est_libere_le_jour_meme_de_sa_date_d_evenement() {
        decorDePlacement();
        nommerLesJours();
        sorti(CENTRE_A, "DECEDE_J-1", "DECEDE", jour(-1));
        sorti(CENTRE_A, "DECEDE_J", "DECEDE", jour(0));
        sorti(CENTRE_A, "DECEDE_J+1", "DECEDE", jour(1));
        sorti(CENTRE_A, "GREFFE_J", "GREFFE", jour(0));
        sorti(CENTRE_A, "GREFFE_J+1", "GREFFE", jour(1));
        sorti(CENTRE_A, "GREFFE_J-3", "GREFFE", jour(-3));
        passerLaNuit("decede_greffe");
    }

    @Test
    void un_sorti_sans_date_d_evenement_est_libere_immediatement() {
        decorDePlacement();
        nommerLesJours();
        sorti(CENTRE_A, "TRANSFERE_SANS_DATE", "TRANSFERE", null);
        sorti(CENTRE_A, "DECEDE_SANS_DATE", "DECEDE", null);
        sorti(CENTRE_A, "GREFFE_SANS_DATE", "GREFFE", null);
        sorti(CENTRE_A, "GUERRI_SANS_DATE", "GUERRI", null);
        passerLaNuit("sans_date");
    }

    // ------------------------------------------------------------------ séjours limités et états sans libération

    @Test
    void un_sejour_limite_est_libere_apres_son_dernier_jour_inclus_et_jamais_sans_date_de_fin() {
        decorDePlacement();
        nommerLesJours();
        sorti(CENTRE_A, "OCCASIONNEL_FIN_J-1", "OCCASIONNEL", jour(-1));
        sorti(CENTRE_A, "OCCASIONNEL_FIN_J", "OCCASIONNEL", jour(0));
        sorti(CENTRE_A, "OCCASIONNEL_SANS_FIN", "OCCASIONNEL", null);
        sorti(CENTRE_A, "VACANCIER_LOCAL_FIN_J-4", "VACANCIER_LOCAL", jour(-4));
        sorti(CENTRE_A, "VACANCIER_LOCAL_FIN_J+4", "VACANCIER_LOCAL", jour(4));
        sorti(CENTRE_A, "VACANCIER_ETRANGER_FIN_J-1", "VACANCIER_ETRANGER", jour(-1));
        sorti(CENTRE_A, "VACANCIER_ETRANGER_SANS_FIN", "VACANCIER_ETRANGER", null);
        passerLaNuit("sejours_limites");
    }

    @Test
    void un_permanent_un_etat_non_renseigne_ou_inconnu_ne_sont_jamais_liberes_et_le_sommeil_n_empeche_pas_la_liberation() {
        decorDePlacement();
        nommerLesJours();
        sorti(CENTRE_A, "PERMANENT_SANS_DATE", "PERMANENT", null);
        sorti(CENTRE_A, "PERMANENT_AVEC_DATE_PASSEE", "PERMANENT", jour(-5));
        sorti(CENTRE_A, "ETAT_NON_RENSEIGNE", null, jour(-5));
        sorti(CENTRE_A, "ETAT_INCONNU", "SUSPENDU", jour(-5));
        sorti(CENTRE_A, "ETAT_EN_MINUSCULES", "transfere", jour(-5));
        UUID enSommeil = sorti(CENTRE_A, "TRANSFERE_EN_SOMMEIL", "TRANSFERE", jour(-5));
        sommeil(enSommeil);
        UUID sommeilPermanent = sorti(CENTRE_A, "PERMANENT_EN_SOMMEIL", "PERMANENT", null);
        sommeil(sommeilPermanent);
        passerLaNuit("jamais_liberes");
    }

    // ------------------------------------------------------------------ forme de la place et du mouvement

    @Test
    void une_place_partielle_est_liberee_et_le_mouvement_porte_ce_qui_etait_occupe() {
        decorDePlacement();
        nommerLesJours();
        UUID seulGenerateur = sorti(CENTRE_A, "SEUL_GENERATEUR", "TRANSFERE", jour(-2));
        jdbc.update("UPDATE patients SET salle_id = NULL, position_id = NULL WHERE id = ?", seulGenerateur);
        UUID seulCreneau = sorti(CENTRE_A, "SEUL_CRENEAU", "TRANSFERE", jour(-2));
        jdbc.update("UPDATE patients SET salle_id = NULL, generateur_id = NULL WHERE id = ?", seulCreneau);
        UUID seuleSalle = sorti(CENTRE_A, "SEULE_SALLE", "TRANSFERE", jour(-2));
        jdbc.update("UPDATE patients SET position_id = NULL, generateur_id = NULL WHERE id = ?", seuleSalle);
        UUID sansJours = sorti(CENTRE_A, "SANS_JOURS", "TRANSFERE", jour(-2));
        jdbc.update("UPDATE patients SET jour_lundi = FALSE, jour_mercredi = FALSE WHERE id = ?", sansJours);
        UUID tousLesJours = sorti(CENTRE_A, "TOUS_LES_JOURS", "TRANSFERE", jour(-2));
        jdbc.update("UPDATE patients SET jour_dimanche = TRUE, jour_lundi = TRUE, jour_mardi = TRUE, jour_mercredi = TRUE, "
                + "jour_jeudi = TRUE, jour_vendredi = TRUE, jour_samedi = TRUE WHERE id = ?", tousLesJours);
        passerLaNuit("place_partielle");
    }

    @Test
    void un_patient_sorti_sans_aucune_place_est_ignore_et_n_ecrit_aucun_mouvement() {
        decorDePlacement();
        nommerLesJours();
        UUID sansPlace = sorti(CENTRE_A, "SANS_PLACE", "TRANSFERE", jour(-5));
        jdbc.update("UPDATE patients SET salle_id = NULL, position_id = NULL, generateur_id = NULL WHERE id = ?", sansPlace);
        UUID joursSeulement = sorti(CENTRE_A, "JOURS_SANS_PLACE", "TRANSFERE", jour(-5));
        jdbc.update("UPDATE patients SET salle_id = NULL, position_id = NULL, generateur_id = NULL WHERE id = ?", joursSeulement);
        passerLaNuit("sans_place");
    }

    @Test
    void le_mouvement_de_liberation_est_automatique_date_du_jour_et_conserve_l_etat_de_sortie() {
        decorDePlacement();
        nommerLesJours();
        sorti(CENTRE_A, "DECEDE_J-3", "DECEDE", jour(-3));
        sorti(CENTRE_A, "OCCASIONNEL_FIN_J-6", "OCCASIONNEL", jour(-6));
        passerLaNuit("mouvement");
    }

    // ------------------------------------------------------------------ rejeu

    @Test
    void la_liberation_est_idempotente_un_second_passage_n_ecrit_rien() {
        decorDePlacement();
        nommerLesJours();
        sorti(CENTRE_A, "TRANSFERE_J-2", "TRANSFERE", jour(-2));
        sorti(CENTRE_A, "PERMANENT", "PERMANENT", null);
        scheduler.libererPlaces();
        etatDesMouvements("idempotence_apres_le_premier_passage");
        scheduler.libererPlaces();
        scheduler.libererPlaces();
        etatDesPatients("idempotence_patients");
        etatDesMouvements("idempotence_mouvements");
    }

    @Test
    void un_patient_replace_apres_sa_liberation_est_libere_de_nouveau_avec_un_second_mouvement() {
        decorDePlacement();
        nommerLesJours();
        UUID p = sorti(CENTRE_A, "TRANSFERE_J-2", "TRANSFERE", jour(-2));
        scheduler.libererPlaces();
        jdbc.update("UPDATE patients SET salle_id = ?, position_id = ?, generateur_id = ?, jour_mardi = TRUE WHERE id = ?", salle2,
                apresMidi, g2a, p);
        scheduler.libererPlaces();
        etatDesPatients("replace_patients");
        etatDesMouvements("replace_mouvements");
    }

    @Test
    void le_service_compte_les_places_liberees_et_ne_traite_que_le_centre_demande() {
        decorDePlacement();
        nommerLesJours();
        sorti(CENTRE_A, "TRANSFERE_J-2", "TRANSFERE", jour(-2));
        sorti(CENTRE_A, "DECEDE_J", "DECEDE", jour(0));
        sorti(CENTRE_A, "TRANSFERE_J", "TRANSFERE", jour(0));
        sorti(CENTRE_B, "TRANSFERE_B_J-2", "TRANSFERE", jour(-2));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("centresAvecPlaces", service.centres().stream().filter(c -> c.value().equals(CENTRE_A) || c.value().equals(CENTRE_B))
                .map(c -> c.value().equals(CENTRE_A) ? "<CENTRE_A>" : "<CENTRE_B>").sorted().toList());
        m.put("liberees_centre_A", service.libererCentre(com.hemodialyse.backend.domain.shared.vo.CenterId.of(CENTRE_A)));
        m.put("liberees_centre_A_au_second_appel", service.libererCentre(com.hemodialyse.backend.domain.shared.vo.CenterId.of(CENTRE_A)));
        m.put("patients_encore_places_A", patientsPlaces(CENTRE_A));
        m.put("patients_encore_places_B", patientsPlaces(CENTRE_B));
        m.put("centresAvecPlacesApres", service.centres().stream().filter(c -> c.value().equals(CENTRE_A) || c.value().equals(CENTRE_B))
                .map(c -> c.value().equals(CENTRE_A) ? "<CENTRE_A>" : "<CENTRE_B>").sorted().toList());
        arbre("service_par_centre", m);
    }
}
