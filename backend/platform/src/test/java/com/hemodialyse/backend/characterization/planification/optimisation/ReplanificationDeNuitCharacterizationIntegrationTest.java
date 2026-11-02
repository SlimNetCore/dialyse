package com.hemodialyse.backend.characterization.planification.optimisation;

import com.hemodialyse.backend.characterization.planification.OptimisationCharacterizationSupport;
import com.hemodialyse.backend.contexts.planification.interfaces.jobs.ReplanificationDe0230;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Filet de caractérisation P8, lot 3 : replanification automatique de nuit ({@link com.hemodialyse.backend.contexts.planification.interfaces.jobs.ReplanificationDe0230}, appel
 * direct de la méthode du job ; le cron n'est pas attendu). Enchaînement COUVERTURE puis MAINTENANCE puis PATIENTS par centre ayant
 * activé l'option, bilan toujours notifié, propositions signalées mais jamais appliquées.
 * <p>
 * Les calculs d'un enchaînement durent 30 s au plus chacun : les jeux de données sont vides (calcul immédiat) sauf le scénario de
 * la proposition à examiner (un patient en attente, une dizaine de secondes).
 */
class ReplanificationDeNuitCharacterizationIntegrationTest extends OptimisationCharacterizationSupport {

    @Autowired
    private ReplanificationDe0230 job;
    @Autowired
    private Environment environnement;

    @Override
    protected String theme() {
        return "p8-optimisation/nuit";
    }

    private void optionActive(UUID centre) {
        jdbc.update("DELETE FROM planification_reglages WHERE center_id = ?", centre);
        jdbc.update("INSERT INTO planification_reglages (center_id, replanification_auto, heures_par_vacation, heures_hebdo_temps_plein, "
                + "repos_hebdo_min) VALUES (?, TRUE, 5, 40, 1)", centre);
    }

    private void etatDesCalculs(String name) {
        sql(name, "SELECT center_id, perimetre, statut, lance_par, phase, score, erreur, (applique_le IS NOT NULL) AS appliquee, "
                + "parametres FROM planification_optimisation WHERE center_id IN ('" + CENTRE_A + "','" + CENTRE_B
                + "') ORDER BY center_id, perimetre, cree_le, id");
    }

    private int semainesDeCouverture() throws Exception {
        String parametres = jdbc.queryForObject("SELECT parametres FROM planification_optimisation WHERE center_id = ? AND perimetre = "
                + "'COUVERTURE'", String.class, CENTRE_A);
        return mapper.readTree(parametres).path("nbSemaines").asInt();
    }

    // ------------------------------------------------------------------ calendrier du job

    @Test
    void le_job_de_nuit_est_planifie_a_0230_dans_le_fuseau_par_defaut_des_centres() throws Exception {
        Scheduled s = ReplanificationDe0230.class.getMethod("replanifier").getAnnotation(Scheduled.class);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cron", s.cron());
        m.put("zone declaree", s.zone());
        m.put("zone resolue", environnement.resolvePlaceholders(s.zone()));
        arbre("calendrier_du_job", m);
    }

    // ------------------------------------------------------------------ centres concernés

    @Test
    void seuls_les_centres_qui_ont_active_l_option_sont_replanifies() throws Exception {
        petitDecor();
        nommerLesSemaines(0, 3);
        jdbc.update("DELETE FROM planification_reglages WHERE center_id = ?", CENTRE_A);
        jdbc.update("INSERT INTO planification_reglages (center_id, replanification_auto, heures_par_vacation, heures_hebdo_temps_plein, "
                + "repos_hebdo_min) VALUES (?, FALSE, 5, 40, 1)", CENTRE_A);
        job.replanifier();
        // le lancement est synchrone : l'absence de calcul se constate dès le retour, une fois qu'aucun n'est plus en cours (borné à 10 s)
        long limite = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < limite && jdbc.queryForObject(
                "SELECT COUNT(*) FROM planification_optimisation WHERE center_id = ? AND statut = 'EN_COURS'", Integer.class, CENTRE_A) > 0) {
            Thread.onSpinWait();
        }
        etatDesCalculs("calculs_sans_option");
        notifications("notifications_sans_option");
    }

    // ------------------------------------------------------------------ enchaînement

    @Test
    void un_centre_sans_rien_a_calculer_recoit_trois_calculs_de_deux_semaines_et_un_bilan_rien() throws Exception {
        petitDecor();
        nommerLesSemaines(0, 3);
        optionActive(CENTRE_A);
        jdbc.update("UPDATE planification_reglages SET heures_par_vacation = 3, heures_hebdo_temps_plein = 35, repos_hebdo_min = 2 "
                + "WHERE center_id = ?", CENTRE_A);
        job.replanifier();
        attendreLesBilans(1);
        etatDesCalculs("calculs_de_la_nuit");
        notifications("notifications_de_la_nuit");
        diffusionsCapturees("diffusions_de_la_nuit");
        etatDesPlacesDesPatients("patients_apres_la_nuit");
    }

    @Test
    void une_proposition_qui_apporte_quelque_chose_est_signalee_a_l_administration_mais_jamais_appliquee() throws Exception {
        petitDecor();
        nommerLesSemaines(0, 3);
        patient(CENTRE_A, "ATTENTE", "Attente", "Alice", "PERMANENT", matin, "lundi", "mercredi", "vendredi");
        optionActive(CENTRE_A);
        long debut = System.currentTimeMillis();
        job.replanifier();
        attendreLesBilans(1);
        long duree = System.currentTimeMillis() - debut;
        etatDesCalculs("calculs_avec_proposition");
        notifications("notifications_avec_proposition");
        etatDesPlacesDesPatients("patients_apres_la_proposition_rien_n_est_applique");
        // le solveur reçoit 30 s par phase mais s'arrête dès qu'il ne s'améliore plus (10 s)
        org.junit.jupiter.api.Assertions.assertTrue(duree < 60_000, "la nuit a duré " + duree + " ms");
    }

    @Test
    void la_couverture_va_jusqu_a_la_fin_de_la_derniere_absence_a_venir_entre_deux_et_huit_semaines() throws Exception {
        Map<String, Object> issues = new LinkedHashMap<>();
        Map<String, Runnable> cas = new LinkedHashMap<>();
        UUID[] infirmier = new UUID[1];
        for (String nom : List.of("aucune absence", "absence qui se termine dans la semaine 4", "absence deja terminee (semaine -2)",
                "absence qui se termine dans la semaine 12", "absence de la semaine 1 et de la semaine 4")) {
            reinitialiser();
            petitDecor();
            nommerLesSemaines(-2, 12);
            infirmier[0] = inf(CENTRE_A, "INF_A", "Alpha", "Anne", false);
            optionActive(CENTRE_A);
            switch (nom) {
                case "absence qui se termine dans la semaine 4" ->
                        absence(CENTRE_A, "ABS", infirmier[0], semaine(1, JourSemaine.LUNDI), semaine(4, JourSemaine.MERCREDI), "CONGE", "x");
                case "absence deja terminee (semaine -2)" ->
                        absence(CENTRE_A, "ABS", infirmier[0], semaine(-3, JourSemaine.LUNDI), semaine(-2, JourSemaine.MERCREDI), "CONGE", "x");
                case "absence qui se termine dans la semaine 12" ->
                        absence(CENTRE_A, "ABS", infirmier[0], semaine(1, JourSemaine.LUNDI), semaine(12, JourSemaine.MERCREDI), "CONGE", "x");
                case "absence de la semaine 1 et de la semaine 4" -> {
                    absence(CENTRE_A, "ABS_1", infirmier[0], semaine(1, JourSemaine.LUNDI), semaine(1, JourSemaine.MERCREDI), "CONGE", "x");
                    absence(CENTRE_A, "ABS_4", infirmier[0], semaine(4, JourSemaine.LUNDI), semaine(4, JourSemaine.MERCREDI), "CONGE", "x");
                }
                default -> {
                }
            }
            job.replanifier();
            attendreLesBilans(1);
            issues.put(nom, semainesDeCouverture() + " semaine(s)");
        }
        arbre("horizon_de_la_couverture", issues);
        assertEquals(5, issues.size());
    }

    // ------------------------------------------------------------------ reports, échecs, centres, idempotence

    @Test
    void un_calcul_deja_en_cours_reporte_la_nuit_du_centre() throws Exception {
        petitDecor();
        nommerLesSemaines(0, 3);
        optionActive(CENTRE_A);
        UUID enCours = executionEcrite(CENTRE_A, "LANCE_A_LA_MAIN", "EN_COURS", "PATIENTS", 1, null);
        try {
            job.replanifier();
            attendreLesBilansSansAttendreLesCalculs(1);
            etatDesCalculs("calculs_apres_le_report");
            notifications("notifications_apres_le_report");
        } finally {
            jdbc.update("DELETE FROM planification_optimisation WHERE id = ?", enCours);
        }
    }

    @Test
    void des_reglages_illisibles_font_echouer_les_trois_etapes_sans_arreter_les_autres_centres() throws Exception {
        petitDecor();
        nommerLesSemaines(0, 3);
        salle(CENTRE_B, "SALLE_B4", "Salle B4", "NON");
        optionActive(CENTRE_A);
        optionActive(CENTRE_B);
        jdbc.update("UPDATE planification_reglages SET heures_par_vacation = 0 WHERE center_id = ?", CENTRE_A);
        job.replanifier();
        attendreLesBilans(2);
        etatDesCalculs("calculs_des_deux_centres");
        notifications("notifications_des_deux_centres");
    }

    @Test
    void deux_passages_successifs_relancent_les_calculs_et_un_centre_inactif_est_replanifie_comme_les_autres() throws Exception {
        petitDecor();
        nommerLesSemaines(0, 3);
        salle(CENTRE_B, "SALLE_B5", "Salle B5", "NON");
        optionActive(CENTRE_A);
        optionActive(CENTRE_B);
        jdbc.update("UPDATE centers SET actif = FALSE WHERE id = ?", CENTRE_B);
        job.replanifier();
        attendreLesBilans(2);
        job.replanifier();
        attendreLesBilans(4);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("calculs du centre A", nombreDeCalculs(CENTRE_A));
        m.put("calculs du centre B (inactif)", nombreDeCalculs(CENTRE_B));
        m.put("bilans RIEN", jdbc.queryForObject("SELECT COUNT(*) FROM notification_evenement WHERE type = 'REPLANIFICATION_NOCTURNE' "
                + "AND payload LIKE '%\"issue\":\"RIEN\"%'", Integer.class));
        m.put("propositions signalees", jdbc.queryForObject("SELECT COUNT(*) FROM notification_evenement WHERE type = "
                + "'OPTIMISATION_PROPOSITION'", Integer.class));
        arbre("deux_passages", m);
    }

    private int nombreDeCalculs(UUID centre) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM planification_optimisation WHERE center_id = ?", Integer.class, centre);
    }

    /** Variante pour le report : l'exécution « en cours » écrite à la main ne se termine jamais d'elle-même. */
    private void attendreLesBilansSansAttendreLesCalculs(int n) throws Exception {
        long limite = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < limite) {
            Integer bilans = jdbc.queryForObject("SELECT COUNT(*) FROM notification_evenement WHERE type = 'REPLANIFICATION_NOCTURNE'",
                    Integer.class);
            if (bilans != null && bilans >= n) return;
            Thread.sleep(100);
        }
        throw new IllegalStateException("Bilan absent");
    }
}
