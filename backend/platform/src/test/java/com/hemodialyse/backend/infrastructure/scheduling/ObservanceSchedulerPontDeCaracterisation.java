package com.hemodialyse.backend.infrastructure.scheduling;

import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.UUID;

/**
 * Pont de test (filet de caractérisation DM2) : {@code ObservancePrescriptionScheduler.controlerCentre} est visible du paquet seulement.
 * Ce pont, placé dans le même paquet côté tests, évite de modifier le code de production (aucun seam requis) et d'utiliser la réflexion.
 */
public final class ObservanceSchedulerPontDeCaracterisation {

    private ObservanceSchedulerPontDeCaracterisation() {
    }

    /** Un passage du contrôle de la nuit pour un seul centre (le jour lu est celui de la JVM). */
    public static void controlerCentre(ObservancePrescriptionScheduler planificateur, UUID centre) {
        planificateur.controlerCentre(CenterId.of(centre));
    }
}
