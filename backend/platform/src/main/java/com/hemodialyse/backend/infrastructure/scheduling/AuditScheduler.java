package com.hemodialyse.backend.infrastructure.scheduling;

import com.hemodialyse.backend.application.audit.AuditWriterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cadence de la traçabilité : écriture par lot des évènements en attente (chaque 3 secondes, pour ne jamais
 * ralentir la requête HTTP qui les a produits) et purge nocturne de l'historique au-delà de la rétention configurée.
 */
@Component
public class AuditScheduler {

    private static final Logger log = LoggerFactory.getLogger(AuditScheduler.class);

    private final AuditWriterService writer;
    private final int retentionDays;

    public AuditScheduler(AuditWriterService writer, @Value("${app.audit.retention-days:365}") int retentionDays) {
        this.writer = writer;
        this.retentionDays = retentionDays;
    }

    @Scheduled(fixedDelay = 3000)
    public void flush() {
        writer.flush();
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void purge() {
        int deleted = writer.purgeOlderThan(retentionDays);
        if (deleted > 0) {
            log.info("Journal d'audit : {} entrée(s) de plus de {} jour(s) purgée(s)", deleted, retentionDays);
        }
    }
}
