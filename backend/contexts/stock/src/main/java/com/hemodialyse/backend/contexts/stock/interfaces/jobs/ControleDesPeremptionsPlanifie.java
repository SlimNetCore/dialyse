package com.hemodialyse.backend.contexts.stock.interfaces.jobs;

import com.hemodialyse.backend.contexts.stock.application.peremptions.ControlerLesPeremptions;
import com.hemodialyse.backend.contexts.stock.application.peremptions.ControlerLesPeremptions.ResultatDuCentre;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

/**
 * Adapter entrant planifié (slice S8) : un seul déclencheur serveur à 07:00, fuseau {@code app.stock.fuseau-par-defaut} (par défaut
 * celui des Absences, {@code Africa/Algiers}) ; le cas d'usage calcule « aujourd'hui » au fuseau de chaque centre. Un centre en
 * échec n'arrête pas les autres : l'incident est journalisé.
 */
public class ControleDesPeremptionsPlanifie {

    private static final Logger log = LoggerFactory.getLogger(ControleDesPeremptionsPlanifie.class);

    private final ControlerLesPeremptions controler;

    public ControleDesPeremptionsPlanifie(ControlerLesPeremptions controler) {
        this.controler = controler;
    }

    /** Tous les jours à 07:00. */
    @Scheduled(cron = "0 0 7 * * *", zone = "${app.stock.fuseau-par-defaut:${app.absences.fuseau-par-defaut:Africa/Algiers}}")
    public void controlerLesPeremptions() {
        journaliser(controler.controler());
    }

    static void journaliser(List<ResultatDuCentre> resultats) {
        for (ResultatDuCentre r : resultats) {
            if (r.incident() != null) {
                log.error("[STOCK][PEREMPTIONS] Contrôle impossible pour le centre {} : {}", r.centre().valeur(), r.incident());
            } else if (r.lots() > 0) {
                log.info("[STOCK][PEREMPTIONS] Centre {} ({}) : {} lot(s) signalé(s), alerte {}", r.centre().valeur(), r.jour(),
                        r.lots(), r.notifie() ? "émise" : "déjà émise ce jour");
            }
        }
    }
}
