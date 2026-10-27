package com.hemodialyse.backend.characterization.planification.liberation;

import com.hemodialyse.backend.characterization.planification.LiberationCharacterizationSupport;
import com.hemodialyse.backend.contexts.absencespatients.interfaces.jobs.ControleDeNuitDesAbsences;
import com.hemodialyse.backend.contexts.planification.interfaces.jobs.LiberationDesPlacesDe0400;
import com.hemodialyse.backend.infrastructure.scheduling.ReplanificationAutomatiqueScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Filet de caractérisation P7 : isolation par centre, centres inactifs, jours fermés, échec d'un patient dans le lot (transaction unique par
 * centre, B7), fuseau et calendrier des tâches de nuit, absence de verrou de placement, effets de bord (notifications, diffusions, audit).
 */
class CentresEtEchecsDeLaLiberationCharacterizationIntegrationTest extends LiberationCharacterizationSupport {

    @Autowired
    private TransactionTemplate transaction;

    @Override
    protected String theme() {
        return "p7-liberation/centres";
    }

    // ------------------------------------------------------------------ isolation, centres inactifs, jours fermés

    @Test
    void chaque_centre_libere_ses_propres_patients_sans_toucher_ceux_des_autres() {
        decorDePlacement();
        nommerLesJours();
        sorti(CENTRE_A, "A_TRANSFERE_J-2", "TRANSFERE", jour(-2));
        sorti(CENTRE_A, "A_TRANSFERE_J+2", "TRANSFERE", jour(2));
        sorti(CENTRE_B, "B_DECEDE_J-1", "DECEDE", jour(-1));
        sorti(CENTRE_B, "B_GUERRI_J", "GUERRI", jour(0));
        scheduler.libererPlaces();
        etatDesPatients("deux_centres_patients");
        etatDesMouvements("deux_centres_mouvements");
    }

    @Test
    void un_centre_inactif_ou_sans_ligne_de_centre_est_libere_comme_les_autres() {
        decorDePlacement();
        nommerLesJours();
        sorti(CENTRE_A, "A_TRANSFERE_J-2", "TRANSFERE", jour(-2));
        sorti(CENTRE_B, "B_TRANSFERE_J-2", "TRANSFERE", jour(-2));
        jdbc.update("UPDATE centers SET actif = FALSE WHERE id = ?", CENTRE_B);
        scheduler.libererPlaces();
        etatDesPatients("centre_inactif_patients");
        etatDesMouvements("centre_inactif_mouvements");
    }

    @Test
    void un_centre_ferme_ou_ferie_aujourd_hui_est_libere_comme_un_jour_ouvre() {
        decorDePlacement();
        nommerLesJours();
        jourFerme(CENTRE_A, jour(0), "Jour ferie");
        fermetureExceptionnelle(CENTRE_B, jour(0), "Travaux");
        sorti(CENTRE_A, "A_TRANSFERE_J-2", "TRANSFERE", jour(-2));
        sorti(CENTRE_B, "B_TRANSFERE_J-2", "TRANSFERE", jour(-2));
        scheduler.libererPlaces();
        etatDesPatients("jour_ferme_patients");
        etatDesMouvements("jour_ferme_mouvements");
    }

    // ------------------------------------------------------------------ échec d'un patient dans le lot

    @Test
    void un_patient_en_echec_annule_toutes_les_liberations_de_son_centre_mais_pas_celles_des_autres_centres() {
        decorDePlacement();
        nommerLesJours();
        UUID a1 = sorti(CENTRE_A, "A1_TRANSFERE_J-2", "TRANSFERE", jour(-2));
        UUID a2 = sorti(CENTRE_A, "A2_DECEDE_J-1", "DECEDE", jour(-1));
        UUID a3 = sorti(CENTRE_A, "A3_GUERRI_J-3", "GUERRI", jour(-3));
        sorti(CENTRE_A, "A4_PERMANENT", "PERMANENT", null);
        sorti(CENTRE_B, "B1_TRANSFERE_J-2", "TRANSFERE", jour(-2));
        Map<String, Object> m = new LinkedHashMap<>();
        // l'ordre de traitement des patients du lot n'est pas garanti : on fait échouer chacun des trois à tour de rôle
        for (UUID fautif : List.of(a1, a2, a3)) {
            faireEchouerLeMouvementDe(fautif);
            scheduler.libererPlaces();
            Map<String, Object> passage = new LinkedHashMap<>();
            passage.put("patientsEncorePlacesA", patientsPlaces(CENTRE_A));
            passage.put("mouvementsPlaceLibereeA", mouvementsDeLiberation(CENTRE_A));
            passage.put("mouvementsPlaceLibereeB", mouvementsDeLiberation(CENTRE_B));
            passage.put("patientsEncorePlacesB", patientsPlaces(CENTRE_B));
            m.put("echec_de_" + (fautif.equals(a1) ? "A1" : fautif.equals(a2) ? "A2" : "A3"), passage);
            // remet le centre B dans son état de départ pour que les passages soient comparables
            jdbc.update("DELETE FROM mouvement_patient WHERE center_id = ?", CENTRE_B);
            jdbc.update("UPDATE patients SET salle_id = ?, position_id = ?, generateur_id = NULL, jour_lundi = TRUE, jour_mercredi = TRUE "
                    + "WHERE center_id = ? AND nom LIKE 'Patient B1%'", salleB, creneauB, CENTRE_B);
            // P7 (une transaction par patient) : les autres patients du centre A sont libérés à chaque passage ; on remet le centre A
            // dans son état de départ pour pouvoir faire échouer le patient suivant (la contrainte ne peut pas se poser sur un patient
            // qui a déjà son mouvement)
            jdbc.update("DELETE FROM mouvement_patient WHERE center_id = ?", CENTRE_A);
            jdbc.update("UPDATE patients SET salle_id = ?, position_id = ?, generateur_id = ?, jour_lundi = TRUE, jour_mercredi = TRUE "
                    + "WHERE center_id = ? AND nom LIKE 'Patient A%'", salle1, matin, g1a, CENTRE_A);
        }
        arbre("passages_en_echec", m);
        etatDesPatients("apres_les_echecs_patients");
        retirerLEchec();
        scheduler.libererPlaces();
        etatDesPatients("apres_reprise_patients");
        etatDesMouvements("apres_reprise_mouvements");
    }

    @Test
    void un_centre_en_echec_est_retente_a_chaque_passage_et_ne_libere_rien_tant_que_la_cause_subsiste() {
        decorDePlacement();
        nommerLesJours();
        UUID a1 = sorti(CENTRE_A, "A1_TRANSFERE_J-2", "TRANSFERE", jour(-2));
        sorti(CENTRE_A, "A2_TRANSFERE_J-2", "TRANSFERE", jour(-2));
        faireEchouerLeMouvementDe(a1);
        List<Integer> placesApresChaquePassage = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            scheduler.libererPlaces();
            placesApresChaquePassage.add(patientsPlaces(CENTRE_A));
        }
        arbre("passages_repetes", Map.of("patientsEncorePlacesA", placesApresChaquePassage, "mouvements", mouvementsDeLiberation(CENTRE_A)));
    }

    // ------------------------------------------------------------------ fuseau et calendrier

    @Test
    void la_date_du_jour_du_job_et_du_mouvement_est_celle_du_fuseau_utc() {
        decorDePlacement();
        // pas de nommerLesJours() : ce scénario n'est pas ignoré entre 23:00 et 24:00 UTC (il ne lit que le jour UTC)
        LocalDate utc = LocalDate.now(ZoneOffset.UTC);
        alias(utc.minusDays(1), "<UTC-1>");
        alias(utc, "<UTC>");
        alias(utc.plusDays(1), "<UTC+1>");
        sorti(CENTRE_A, "TRANSFERE_UTC-1", "TRANSFERE", utc.minusDays(1));
        sorti(CENTRE_A, "TRANSFERE_UTC", "TRANSFERE", utc);
        sorti(CENTRE_A, "DECEDE_UTC", "DECEDE", utc);
        sorti(CENTRE_A, "DECEDE_UTC+1", "DECEDE", utc.plusDays(1));
        scheduler.libererPlaces();
        etatDesPatients("fuseau_patients");
        etatDesMouvements("fuseau_mouvements");
    }

    @Test
    void le_calendrier_de_nuit_le_cron_de_liberation_n_a_pas_de_fuseau_les_controles_de_02h30_ont_le_leur() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("liberation", planification(LiberationDesPlacesDe0400.class, "libererLesPlaces"));
        m.put("controleDesAbsences", planification(ControleDeNuitDesAbsences.class, "controlerAbsences"));
        m.put("replanificationAutomatique", planification(ReplanificationAutomatiqueScheduler.class, "replanifier"));
        arbre("calendrier_de_nuit", m);
    }

    private Map<String, String> planification(Class<?> type, String methode) throws Exception {
        Scheduled s = type.getMethod(methode).getAnnotation(Scheduled.class);
        Map<String, String> m = new LinkedHashMap<>();
        m.put("cron", s.cron());
        m.put("zone", s.zone().isEmpty() ? "(fuseau par defaut de la JVM)" : s.zone());
        return m;
    }

    // ------------------------------------------------------------------ concurrence et effets de bord

    @Test
    void la_liberation_ne_prend_pas_le_verrou_de_placement_du_centre_et_s_acheve_pendant_qu_il_est_tenu() throws Exception {
        decorDePlacement();
        nommerLesJours();
        joursOuvertsDuCentre(CENTRE_A, TOUS_LES_JOURS);
        sorti(CENTRE_A, "A_TRANSFERE_J-2", "TRANSFERE", jour(-2));
        String issue = transaction.execute(statut -> {
            jdbc.queryForList("SELECT center_id FROM planning_parametres WHERE center_id = ? FOR UPDATE", CENTRE_A);
            CompletableFuture<Void> nuit = CompletableFuture.runAsync(scheduler::libererPlaces);
            try {
                nuit.get(20, TimeUnit.SECONDS);
                return "liberation terminee pendant que le verrou du centre est tenu";
            } catch (TimeoutException e) {
                return "liberation bloquee par le verrou du centre";
            } catch (Exception e) {
                return "erreur " + e.getClass().getSimpleName();
            }
        });
        arbre("verrou_de_placement", Map.of("issue", issue, "patientsEncorePlacesA", patientsPlaces(CENTRE_A)));
    }

    @Test
    void la_liberation_ne_produit_ni_notification_ni_diffusion_ni_audit() {
        decorDePlacement();
        nommerLesJours();
        monterLAudit();
        sorti(CENTRE_A, "A_TRANSFERE_J-2", "TRANSFERE", jour(-2));
        sorti(CENTRE_B, "B_TRANSFERE_J-2", "TRANSFERE", jour(-2));
        diffusions.clear();
        scheduler.libererPlaces();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("notifications", nombreDe("notification_evenement"));
        m.put("auditLog", auditNombre());
        m.put("diffusions", new ArrayList<>(diffusions));
        m.put("evenementsEnAttente", jdbc.queryForObject("SELECT COUNT(*) FROM event_publication", Integer.class));
        arbre("effets_de_bord", m);
    }

    private int auditNombre() {
        viderLAudit();
        return nombreDe("audit_log");
    }
}
