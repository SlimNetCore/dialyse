package com.hemodialyse.backend.contexts.planification.interfaces.jobs;

import com.hemodialyse.backend.contexts.planification.application.ReplanifierLaNuit;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Adapter entrant planifié (slice P8) : la replanification de nuit des centres actifs qui l'ont activée, à 02:30 au fuseau
 * {@code app.planification.fuseau-par-defaut} (Africa/Algiers, comme les deux autres jobs de nuit). « Aujourd'hui » est le jour de
 * chaque centre ; un centre en erreur n'arrête pas les autres (journalisé par le cas d'usage). Les propositions sont signalées, jamais
 * appliquées d'office.
 */
public class ReplanificationDe0230 {

    private final ReplanifierLaNuit replanifier;

    public ReplanificationDe0230(ReplanifierLaNuit replanifier) {
        this.replanifier = replanifier;
    }

    /**
     * Toutes les nuits à 02:30.
     */
    @Scheduled(cron = "0 30 2 * * *", zone = "${app.planification.fuseau-par-defaut:Africa/Algiers}")
    public void replanifier() {
        replanifier.executer();
    }
}
