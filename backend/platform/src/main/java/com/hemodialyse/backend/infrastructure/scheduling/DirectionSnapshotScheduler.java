package com.hemodialyse.backend.infrastructure.scheduling;

import com.hemodialyse.backend.application.direction.DirectionSnapshotService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fige chaque nuit du 1er du mois (02:30) les tableaux de bord du mois écoulé de chaque société active. Une société
 * dont l'instantané existe déjà est ignorée : la tâche est rejouable sans effet.
 */
@Component
public class DirectionSnapshotScheduler {

    private final DirectionSnapshotService snapshots;

    public DirectionSnapshotScheduler(DirectionSnapshotService snapshots) {
        this.snapshots = snapshots;
    }

    @Scheduled(cron = "0 30 2 1 * *")
    public void snapshotPreviousMonth() {
        snapshots.generateForPreviousMonth();
    }
}
