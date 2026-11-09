package com.hemodialyse.backend.contexts.pilotagedirection.interfaces.planifie;

import com.hemodialyse.backend.contexts.pilotagedirection.application.CapturerLesInstantanesDuMoisEcoule;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Fige chaque nuit du 1er du mois (02:30, heure du serveur) les instantanés du mois écoulé de chaque société active. Rejouable sans effet.
 * Même expression cron que l'ancien {@code DirectionSnapshotScheduler} (retiré) ; non couverte par le filet (câblage), vérifiée par un
 * test de contrat de l'annotation.
 */
public final class CaptureDuMoisEcoulePlanifiee {

    /** L'expression est une constante pour que son test la lise. */
    public static final String CRON = "0 30 2 1 * *";

    private final CapturerLesInstantanesDuMoisEcoule capture;

    public CaptureDuMoisEcoulePlanifiee(CapturerLesInstantanesDuMoisEcoule capture) {
        this.capture = capture;
    }

    @Scheduled(cron = CRON)
    public void figerLeMoisEcoule() {
        capture.execute();
    }
}
