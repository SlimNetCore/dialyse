package com.hemodialyse.backend.contexts.notifications.application;

import com.hemodialyse.backend.contexts.notifications.domain.DureesDuJournal;

/** Purge quotidienne : supprime les notifications (et leurs lectures) plus anciennes que la durée de conservation, tous centres confondus. */
public class PurgerLesNotifications {

    private final NotificationsDuJournal journal;
    private final Horloge horloge;

    public PurgerLesNotifications(NotificationsDuJournal journal, Horloge horloge) {
        this.journal = journal;
        this.horloge = horloge;
    }

    /** @return nombre de notifications supprimées */
    public int executer() {
        return journal.purger(horloge.maintenant().minus(DureesDuJournal.CONSERVATION));
    }
}
