package com.hemodialyse.backend.infrastructure.scheduling;

import com.hemodialyse.backend.application.planning.optimisation.ReplanificationAutomatiqueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Job nocturne : replanification automatique des centres qui l'ont activée dans leurs réglages d'optimisation
 * (couverture, maintenances, placement des patients). Les propositions sont signalées, jamais appliquées d'office.
 */
@Component
public class ReplanificationAutomatiqueScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReplanificationAutomatiqueScheduler.class);
    private final ReplanificationAutomatiqueService replanification;

    public ReplanificationAutomatiqueScheduler(ReplanificationAutomatiqueService replanification) {
        this.replanification = replanification;
    }

    /**
     * Toutes les nuits à 02:30.
     */
    @Scheduled(cron = "0 30 2 * * *")
    public void replanifier() {
        LocalDate aujourdhui = LocalDate.now(ZoneOffset.UTC);
        for (UUID centre : replanification.centres()) {
            try {
                replanification.replanifier(centre, aujourdhui);
            } catch (RuntimeException e) {
                // un centre en erreur ne doit pas empêcher la replanification des autres
                log.warn("[REPLANIFICATION] Impossible pour le centre {}", centre, e);
            }
        }
    }
}
