package com.hemodialyse.backend.contexts.seance.interfaces.jobs;

import com.hemodialyse.backend.contexts.seance.application.RappelerLesSeancesARegulariser;
import com.hemodialyse.backend.contexts.seance.application.RappelerLesSeancesARegulariser.ResultatDuCentre;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

/**
 * Adapter entrant planifié (slice SE4) : l'UNIQUE déclencheur serveur du rappel des séances à régulariser, tous les jours à 07:00 (après
 * la détection des absences de 02:30 : sans régularisation, ces patients sont comptés absents). Fuseau de la planification
 * {@code app.seance.fuseau-par-defaut} (repli : celui du Stock, puis des Absences, puis {@code Africa/Algiers}) ; le cas d'usage calcule
 * « aujourd'hui » au fuseau de chaque centre (KB-SEA-36 corrigé : le legacy lançait le job au fuseau de la JVM et lisait le jour UTC).
 * Un centre en échec n'arrête pas les autres : l'incident est journalisé, jamais propagé.
 */
public class RappelDesRegularisationsPlanifie {

    private static final Logger log = LoggerFactory.getLogger(RappelDesRegularisationsPlanifie.class);

    private final RappelerLesSeancesARegulariser rappeler;

    public RappelDesRegularisationsPlanifie(RappelerLesSeancesARegulariser rappeler) {
        this.rappeler = rappeler;
    }

    /** Tous les jours à 07:00. */
    @Scheduled(cron = "0 0 7 * * *", zone = "${app.seance.fuseau-par-defaut:${app.stock.fuseau-par-defaut:${app.absences.fuseau-par-defaut:Africa/Algiers}}}")
    public void rappelerLesRegularisations() {
        journaliser(rappeler.executer());
    }

    static void journaliser(List<ResultatDuCentre> resultats) {
        for (ResultatDuCentre r : resultats) {
            if (r.incident() != null) {
                log.warn("[SEANCES] Rappel de régularisation impossible pour le centre {} : {}", r.centre().valeur(), r.incident());
            } else if (r.seances() > 0) {
                log.info("[SEANCES] Centre {} ({}) : {} séance(s) à régulariser, rappel {}", r.centre().valeur(), r.jour(), r.seances(),
                        r.rappele() ? "émis" : "déjà émis ce jour");
            }
        }
    }
}
