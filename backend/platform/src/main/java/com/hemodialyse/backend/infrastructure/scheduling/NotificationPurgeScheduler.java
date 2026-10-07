package com.hemodialyse.backend.infrastructure.scheduling;

import com.hemodialyse.backend.application.notification.NotificationJournalPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Job quotidien : supprime du journal les alertes de plus de 60 jours (le journal n'en propose que 30).
 */
@Component
public class NotificationPurgeScheduler {

    static final Duration CONSERVATION = Duration.ofDays(60);
    private static final Logger log = LoggerFactory.getLogger(NotificationPurgeScheduler.class);
    private final NotificationJournalPort journal;

    public NotificationPurgeScheduler(NotificationJournalPort journal) {
        this.journal = journal;
    }

    /**
     * Tous les jours à 03:40.
     */
    @Scheduled(cron = "0 40 3 * * *")
    public void purger() {
        try {
            int n = journal.purger(Instant.now().minus(CONSERVATION));
            if (n > 0)
                log.info("[NOTIFICATION] {} alerte(s) de plus de {} jours supprimée(s)", n, CONSERVATION.toDays());
        } catch (RuntimeException e) {
            log.warn("[NOTIFICATION] Purge du journal impossible", e);
        }
    }
}
