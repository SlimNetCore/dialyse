package com.hemodialyse.backend.contexts.notifications.contrat;

import com.hemodialyse.backend.contexts.notifications.application.NotificationsDuJournal;
import com.hemodialyse.backend.contexts.notifications.doubles.NotificationsDuJournalEnMemoire;
import org.junit.jupiter.api.BeforeEach;

class NotificationsDuJournalEnMemoireContratTest extends NotificationsDuJournalContrat {

    private NotificationsDuJournalEnMemoire journal;

    @BeforeEach
    void vider() {
        journal = new NotificationsDuJournalEnMemoire();
    }

    @Override
    protected NotificationsDuJournal journal() {
        return journal;
    }

    @Override
    protected int lectures(String utilisateur) {
        return journal.lecturesDe(utilisateur);
    }
}
