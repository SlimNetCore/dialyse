package com.hemodialyse.backend.infrastructure.scheduling;

import com.hemodialyse.backend.application.direction.DirectionRealtimeService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cadence du temps réel de la direction : recalcul rapide des sociétés signalées par un événement (chaque seconde)
 * et balayage de sécurité de toutes les sociétés suivies, pour les modifications qui n'émettent pas d'événement.
 * Sans direction connectée, ces tâches ne font rien.
 */
@Component
public class DirectionRealtimeScheduler {

    private final DirectionRealtimeService realtime;

    public DirectionRealtimeScheduler(DirectionRealtimeService realtime) {
        this.realtime = realtime;
    }

    @Scheduled(fixedDelay = 1000)
    public void processSignalled() {
        realtime.processDirty();
    }

    @Scheduled(fixedDelay = 10000, initialDelay = 10000)
    public void sweep() {
        realtime.sweep();
    }
}
