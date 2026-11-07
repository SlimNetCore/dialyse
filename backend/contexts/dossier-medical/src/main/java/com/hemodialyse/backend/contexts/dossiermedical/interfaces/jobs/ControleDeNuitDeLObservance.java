package com.hemodialyse.backend.contexts.dossiermedical.interfaces.jobs;

import com.hemodialyse.backend.contexts.dossiermedical.application.ControlerLObservance;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Adapter entrant planifié : le contrôle de l'observance du traitement de l'anémie, chaque jour à 06:30 (MÊME cron que le legacy, au
 * fuseau de la JVM comme avant), pour tous les centres. Un centre en échec est journalisé et n'empêche pas les suivants (avant : le
 * premier échec interrompait le passage).
 */
public class ControleDeNuitDeLObservance {

    private static final Logger log = LoggerFactory.getLogger(ControleDeNuitDeLObservance.class);

    private final ControlerLObservance controler;

    public ControleDeNuitDeLObservance(ControlerLObservance controler) {
        this.controler = controler;
    }

    @Scheduled(cron = "0 30 6 * * *")
    public void controlerObservance() {
        for (CentreId centre : controler.centres()) {
            controlerLeCentre(centre);
        }
    }

    /** Un passage pour un seul centre (utilisé aussi par le filet de caractérisation). */
    public void controlerLeCentre(CentreId centre) {
        try {
            controler.controlerLeCentre(centre);
        } catch (RuntimeException e) {
            log.error("[OBSERVANCE] Contrôle impossible pour le centre {}", centre.valeur(), e);
        }
    }
}
