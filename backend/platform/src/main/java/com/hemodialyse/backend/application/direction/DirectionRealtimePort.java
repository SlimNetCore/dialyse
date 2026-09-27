package com.hemodialyse.backend.application.direction;

import com.hemodialyse.backend.application.direction.DashboardDiff.Change;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Port de sortie : diffusion temps réel, vers la direction d'une société, des changements survenus dans les
 * indicateurs de son tableau de bord. Implémenté par l'infrastructure (WebSocket/STOMP) ; le domaine et
 * l'application ne connaissent pas le transport.
 */
public interface DirectionRealtimePort {

    void publish(UUID societeId, DashboardChanged event);

    /**
     * Événement de changement : contient uniquement des indicateurs agrégés et anonymes (jamais de donnée nominative).
     * Le client relit ensuite les données par l'API sécurisée.
     */
    record DashboardChanged(String type, UUID societeId, Instant at, List<Change> changes) {
        public static DashboardChanged of(UUID societeId, List<Change> changes) {
            return new DashboardChanged("DASHBOARD_CHANGED", societeId, Instant.now(), changes);
        }
    }
}
