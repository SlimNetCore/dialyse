package com.hemodialyse.backend.contexts.notifications.interfaces.planifie;

import com.hemodialyse.backend.contexts.notifications.application.PurgerLesNotifications;
import com.hemodialyse.backend.contexts.notifications.domain.DureesDuJournal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** Tâche quotidienne de 03:40 : purge du journal (le journal n'en propose que 30 jours, la conservation est de 60). */
public class PurgeNocturneDesNotifications {

    private static final Logger log = LoggerFactory.getLogger(PurgeNocturneDesNotifications.class);

    private final PurgerLesNotifications purge;

    public PurgeNocturneDesNotifications(PurgerLesNotifications purge) {
        this.purge = purge;
    }

    /** Tous les jours à 03:40. Un échec est journalisé et n'empêche pas la purge de la nuit suivante. */
    @Scheduled(cron = "0 40 3 * * *")
    public void purger() {
        try {
            int n = purge.executer();
            if (n > 0)
                log.info("[NOTIFICATION] {} notification(s) de plus de {} jours supprimée(s)", n, DureesDuJournal.CONSERVATION.toDays());
        } catch (RuntimeException e) {
            log.warn("[NOTIFICATION] Purge du journal impossible", e);
        }
    }
}
