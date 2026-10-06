package com.hemodialyse.backend.infrastructure.scheduling;

import com.hemodialyse.backend.domain.planning.optimisation.port.OptimisationRunRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Au démarrage du serveur, aucun calcul d'optimisation ne peut être en cours : les exécutions restées « en cours »
 * (arrêt du serveur pendant le calcul) sont passées en échec pour ne pas bloquer le lancement d'une nouvelle.
 */
@Component
public class OptimisationPlanningRecovery {

    static final String MOTIF = "Calcul interrompu par l'arrêt du serveur";
    private static final Logger log = LoggerFactory.getLogger(OptimisationPlanningRecovery.class);

    private final OptimisationRunRepositoryPort runs;

    public OptimisationPlanningRecovery(OptimisationRunRepositoryPort runs) {
        this.runs = runs;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void interrompreLesCalculsOrphelins() {
        try {
            int interrompues = runs.interrompreEnCours(MOTIF);
            if (interrompues > 0) log.warn("[OPTIMISATION] {} calcul(s) orphelin(s) marqué(s) en échec", interrompues);
        } catch (RuntimeException e) {
            log.warn("[OPTIMISATION] Reprise impossible", e);
        }
    }
}
