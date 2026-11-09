package com.hemodialyse.backend.characterization.planification.presence;

import com.hemodialyse.backend.characterization.planification.PresenceCharacterizationSupport;
import com.hemodialyse.backend.characterization.planification.JourSemaine;
import com.hemodialyse.backend.contexts.planification.interfaces.jobs.ControleDeLaPresenceDe0715;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Filet de caractérisation P4 : le contrôle quotidien de 07:15 ({@link ControleDeLaPresenceDe0715#controlerLaPresence()}), déclenché
 * par appel direct de la méthode du scheduler (aucun seam). Observé : notifications durables ({@code notification_evenement}),
 * diffusions temps réel du centre, isolation par centre, centres sans infirmier actif ou désactivés, doublons à l'exécution
 * répétée, horizon de 14 jours, jours fermés, ratio configuré, heures par vacation. Famille d'alias {@code <J+n>} ; les patients
 * sont placés sur les sept jours, aucun résultat ne dépend du jour d'exécution. « Aujourd'hui » est le jour UTC du serveur.
 */
class ControleDePresenceDe0715CharacterizationIntegrationTest extends PresenceCharacterizationSupport {

    @Autowired
    private ControleDeLaPresenceDe0715 scheduler;

    @Override
    protected String theme() {
        return "p4-presence/controle-0715";
    }

    private void amorcer() {
        decor();
        nommerJours(-1, 16);
    }

    /** Diffusions du centre A et B uniquement (le canal peut porter d'autres centres de la base partagée). */
    private List<Map<String, Object>> diffusionsDesCentres() {
        List<Map<String, Object>> retenues = new ArrayList<>();
        for (Map<String, Object> d : diffusions) {
            String destination = String.valueOf(d.get("destination"));
            if (destination.contains(CENTRE_A.toString()) || destination.contains(CENTRE_B.toString())) retenues.add(d);
        }
        return retenues;
    }

    private void observer(String nom) {
        notifications(nom + "_notifications");
        arbre(nom + "_diffusions", diffusionsDesCentres());
    }

    // ------------------------------------------------------------------ déclaration du job

    @Test
    void le_job_est_declare_a_07h15_tous_les_jours_au_fuseau_d_alger_par_defaut() throws Exception {
        Scheduled s = ControleDeLaPresenceDe0715.class.getMethod("controlerLaPresence").getAnnotation(Scheduled.class);
        arbre("declaration_du_job", Map.of("cron", s.cron(), "zone", s.zone(), "fixedRate", s.fixedRate(), "fixedDelay", s.fixedDelay()));
    }

    // ------------------------------------------------------------------ sous-effectif

    @Test
    void un_sous_effectif_dans_les_quatorze_jours_notifie_l_administration_le_secretariat_et_les_medecins() throws Exception {
        amorcer();
        infirmierAffecte(CENTRE_A, "INF_A", "Amrani", "Sara", false, salle2, matin, tousLesJours());
        patientsPlaces(CENTRE_A, "PAT", 5, salle1, matin, JourSemaine.values());
        scheduler.controlerLaPresence();
        observer("sous_effectif");
    }

    @Test
    void l_horizon_est_de_quatorze_jours_aujourd_hui_inclus() throws Exception {
        amorcer();
        UUID a = infirmierAffecte(CENTRE_A, "INF_A", "Amrani", "Sara", false, salle1, matin, tousLesJours());
        patientsPlaces(CENTRE_A, "PAT", 4, salle1, matin, JourSemaine.values());
        absence(CENTRE_A, "ABS_J14", a, jour(14), jour(14), "CONGE", null);       // hors horizon
        scheduler.controlerLaPresence();
        notifications("absence_au_quinzieme_jour_aucune_notification");
        absence(CENTRE_A, "ABS_J13", a, jour(13), jour(13), "CONGE", null);       // dernier jour de l'horizon
        scheduler.controlerLaPresence();
        notifications("absence_au_quatorzieme_jour_une_notification");
    }

    @Test
    void sans_sous_effectif_ni_sureffectif_aucune_notification_et_aucune_diffusion() throws Exception {
        amorcer();
        infirmierAffecte(CENTRE_A, "INF_A", "Amrani", "Sara", false, salle1, matin, tousLesJours());
        patientsPlaces(CENTRE_A, "PAT", 4, salle1, matin, JourSemaine.values());
        scheduler.controlerLaPresence();
        observer("tout_couvert");
    }

    // ------------------------------------------------------------------ sur-effectif

    @Test
    void un_sureffectif_notifie_l_administrateur_avec_les_vacations_et_les_heures_du_reglage_du_centre() throws Exception {
        amorcer();
        infirmierAffecte(CENTRE_A, "INF_A", "Amrani", "Sara", false, salle1, matin, tousLesJours());
        infirmierAffecte(CENTRE_A, "INF_B", "Benali", "Karim", false, salle2, apresMidi, "LUNDI,MARDI");   // salle sans patient
        scheduler.controlerLaPresence();
        notifications("sureffectif_heures_par_defaut");
        heuresParVacation(CENTRE_A, 7);
        // l'alerte est unique par centre et par jour (KB-PLN-46) : on efface celle du jour pour relire les heures du nouveau réglage
        jdbc.update("DELETE FROM notification_evenement WHERE center_id = ?", CENTRE_A);
        scheduler.controlerLaPresence();
        notifications("sureffectif_apres_reglage_a_7_heures");
    }

    @Test
    void les_deux_controles_sont_independants_et_un_jour_ferme_ne_compte_ni_manque_ni_surplus() throws Exception {
        amorcer();
        infirmierAffecte(CENTRE_A, "INF_A", "Amrani", "Sara", false, salle2, matin, tousLesJours());
        patientsPlaces(CENTRE_A, "PAT", 5, salle1, matin, JourSemaine.values());
        for (int i = 0; i <= 13; i++) jourFerme(CENTRE_A, jour(i), "Ferie " + i);
        scheduler.controlerLaPresence();
        observer("tout_ferme_aucune_alerte");
        jdbc.update("DELETE FROM center_holiday WHERE center_id = ?", CENTRE_A);
        jourFerme(CENTRE_A, jour(0), "Ferie");
        scheduler.controlerLaPresence();
        notifications("seul_aujourd_hui_ferme");
    }

    @Test
    void le_ratio_du_centre_change_les_notifications() throws Exception {
        amorcer();
        infirmierAffecte(CENTRE_A, "INF_A", "Amrani", "Sara", false, salle1, matin, tousLesJours());
        patientsPlaces(CENTRE_A, "PAT", 6, salle1, matin, JourSemaine.values());
        ratio(CENTRE_A, 6);
        scheduler.controlerLaPresence();
        notifications("ratio_6_aucune_alerte");
        ratio(CENTRE_A, 2);
        scheduler.controlerLaPresence();
        notifications("ratio_2_sous_effectif");
    }

    // ------------------------------------------------------------------ centres

    @Test
    void un_centre_actif_sans_infirmier_est_controle_et_ses_patients_sans_soignant_sont_signales() throws Exception {
        amorcer();
        patientsPlaces(CENTRE_A, "PAT", 8, salle1, matin, JourSemaine.values());
        fiche(CENTRE_A, "INF_OFF", "INF_OFF", "Desactive", "Nour", null, "INFIRMIER", false, false, null);
        scheduler.controlerLaPresence();
        observer("centre_sans_infirmier_actif");
    }

    @Test
    void chaque_centre_actif_est_controle_avec_ses_propres_donnees_et_un_centre_desactive_ne_l_est_pas() throws Exception {
        amorcer();
        infirmierAffecte(CENTRE_A, "INF_A", "Amrani", "Sara", false, salle2, matin, tousLesJours());
        patientsPlaces(CENTRE_A, "PAT_A", 5, salle1, matin, JourSemaine.values());
        infirmierAffecte(CENTRE_B, "INF_B", "Brahimi", "Nadia", false, salleB, creneauB, tousLesJours());
        patientsPlaces(CENTRE_B, "PAT_B", 9, salleB, creneauB, JourSemaine.values());
        jdbc.update("UPDATE centers SET actif = FALSE WHERE id = ?", CENTRE_B);
        scheduler.controlerLaPresence();
        observer("deux_centres_dont_un_desactive");
    }

    @Test
    void un_ratio_illisible_en_base_vaut_le_defaut_et_le_centre_est_controle_comme_les_autres() throws Exception {
        amorcer();
        infirmierAffecte(CENTRE_A, "INF_A", "Amrani", "Sara", false, salle2, matin, tousLesJours());
        patientsPlaces(CENTRE_A, "PAT_A", 5, salle1, matin, JourSemaine.values());
        infirmierAffecte(CENTRE_B, "INF_B", "Brahimi", "Nadia", false, salleB, creneauB, tousLesJours());
        patientsPlaces(CENTRE_B, "PAT_B", 9, salleB, creneauB, JourSemaine.values());
        ratio(CENTRE_B, 0);   // ratio illisible en base : lu comme le défaut 4 (journalisé), le centre B est notifié comme le centre A
        scheduler.controlerLaPresence();
        observer("echec_du_centre_B");
    }

    /** Isolation d'un centre en échec, sans seam : une fiche à la qualification illisible fait échouer la lecture de ce centre seulement. */
    @Test
    void un_centre_dont_les_donnees_sont_illisibles_n_empeche_pas_le_controle_des_autres_centres() throws Exception {
        amorcer();
        infirmierAffecte(CENTRE_A, "INF_A", "Amrani", "Sara", false, salle2, matin, tousLesJours());
        patientsPlaces(CENTRE_A, "PAT_A", 5, salle1, matin, JourSemaine.values());
        patientsPlaces(CENTRE_B, "PAT_B", 9, salleB, creneauB, JourSemaine.values());
        fiche(CENTRE_B, "INF_B_ILLISIBLE", "INF_B_ILLISIBLE", "Illisible", "Nour", null, "INCONNUE", false, true, null);
        scheduler.controlerLaPresence();
        observer("centre_B_illisible_isole");
    }

    // ------------------------------------------------------------------ doublons

    @Test
    void plusieurs_executions_le_meme_jour_n_emettent_qu_une_alerte_par_type_et_par_centre() throws Exception {
        amorcer();
        infirmierAffecte(CENTRE_A, "INF_A", "Amrani", "Sara", false, salle2, matin, tousLesJours());
        patientsPlaces(CENTRE_A, "PAT", 5, salle1, matin, JourSemaine.values());
        infirmierAffecte(CENTRE_A, "INF_B", "Benali", "Karim", false, salle2, apresMidi, "LUNDI");
        scheduler.controlerLaPresence();
        int premiere = nombreDe("notification_evenement");
        scheduler.controlerLaPresence();
        scheduler.controlerLaPresence();
        arbre("executions_repetees", Map.of("notificationsApres1", premiere, "notificationsApres3", nombreDe("notification_evenement"),
                "identifiantsDistincts", jdbc.queryForObject("SELECT COUNT(DISTINCT id) FROM notification_evenement WHERE center_id = ?",
                        Integer.class, CENTRE_A)));
        notifications("executions_repetees_notifications");
    }

    @Test
    void le_controle_n_ecrit_rien_dans_les_tables_du_roulement_ni_des_absences() throws Exception {
        amorcer();
        infirmierAffecte(CENTRE_A, "INF_A", "Amrani", "Sara", false, salle2, matin, tousLesJours());
        patientsPlaces(CENTRE_A, "PAT", 5, salle1, matin, JourSemaine.values());
        String avant = empreinteP3();
        scheduler.controlerLaPresence();
        arbre("tables_inchangees", Map.of("empreinteIdentique", avant.equals(empreinteP3())));
        Map<String, Object> resume = new LinkedHashMap<>();
        resume.put("diffusions", diffusionsDesCentres().size());
        arbre("diffusions_comptees", resume);
    }
}
