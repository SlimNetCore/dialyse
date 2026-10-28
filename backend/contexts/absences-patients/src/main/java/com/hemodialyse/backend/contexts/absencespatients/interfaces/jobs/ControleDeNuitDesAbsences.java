package com.hemodialyse.backend.contexts.absencespatients.interfaces.jobs;

import com.hemodialyse.backend.contexts.absencespatients.application.ControlerLesAbsences;
import com.hemodialyse.backend.contexts.absencespatients.application.ControlerLesAbsences.ResultatDuCentre;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Adapter entrant planifié (slice A3) : un seul déclencheur serveur à 02:30 (fuseau {@code app.absences.fuseau-par-defaut}), le
 * cas d'usage calcule « aujourd'hui » et la fenêtre de chaque centre à son propre fuseau. Au démarrage du serveur, rattrapage d'un
 * mois sans notification (désactivable par {@code app.absences.startup-catchup=false}). Les incidents ne sont que journalisés :
 * un centre ou une absence en échec n'arrête pas le reste.
 */
@Component
public class ControleDeNuitDesAbsences {

    private static final Logger log = LoggerFactory.getLogger(ControleDeNuitDesAbsences.class);

    @Value("${app.absences.startup-catchup:true}")
    private boolean rattrapageAuDemarrage = true;

    private final ControlerLesAbsences controler;

    public ControleDeNuitDesAbsences(ControlerLesAbsences controler) {
        this.controler = controler;
    }

    /** Tous les jours à 02:30. */
    @Scheduled(cron = "0 30 2 * * *", zone = "${app.absences.fuseau-par-defaut:${app.planification.fuseau-par-defaut:Africa/Algiers}}")
    public void controlerAbsences() {
        journaliser("nuit", controler.controlerLaNuit());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void rattraperAuDemarrage() {
        if (!rattrapageAuDemarrage) return;
        journaliser("démarrage", controler.rattraperAuDemarrage());
    }

    private static void journaliser(String passage, List<ResultatDuCentre> resultats) {
        for (ResultatDuCentre resultat : resultats) {
            if (resultat.incident() != null) {
                log.warn("[ABSENCES] Contrôle ({}) impossible pour le centre {} : {}", passage, resultat.centre().valeur(),
                        resultat.incident());
            }
            if (!resultat.bilan().echecs().isEmpty()) {
                // reprise : l'absence est retentée chaque nuit tant que son jour reste dans la fenêtre (7 jours) ; au-delà, relancer
                // POST /rattrapage-detection (la réponse porte alors `echecs`) une fois la cause corrigée
                log.error("[ABSENCES] Contrôle ({}) : {} absence(s) en échec pour le centre {}, reprises chaque nuit pendant {} jours puis à relancer par le rattrapage de détection",
                        passage, resultat.bilan().echecs().size(), resultat.centre().valeur(), ControlerLesAbsences.JOURS_DE_LA_NUIT);
            }
            resultat.bilan().echecs().forEach(echec -> log.warn(
                    "[ABSENCES] Contrôle ({}) : {} impossible, centre {}, patient {}, jour {} : {}", passage,
                    echec.operation(), resultat.centre().valeur(), echec.patient().valeur(), echec.jour(), echec.cause()));
            if (!resultat.bilan().joursIgnores().isEmpty()) {
                log.info("[ABSENCES] Contrôle ({}) : centre {}, jours ignorés (mois d'absences clos) : {}", passage,
                        resultat.centre().valeur(), resultat.bilan().joursIgnores());
            }
        }
    }
}
