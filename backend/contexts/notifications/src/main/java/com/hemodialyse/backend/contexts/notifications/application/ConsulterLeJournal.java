package com.hemodialyse.backend.contexts.notifications.application;

import com.hemodialyse.backend.contexts.notifications.domain.DureesDuJournal;
import com.hemodialyse.backend.contexts.notifications.domain.PaginationDuJournal;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.util.UUID;

/** Consulter le journal : les notifications des 30 derniers jours du centre de la session, visibles par l'utilisateur, avec son état de lecture. */
public class ConsulterLeJournal {

    private final SessionCourante session;
    private final NotificationsDuJournal journal;
    private final Horloge horloge;

    public ConsulterLeJournal(SessionCourante session, NotificationsDuJournal journal, Horloge horloge) {
        this.session = session;
        this.journal = journal;
        this.horloge = horloge;
    }

    public Consultation executer(UUID centreDemande, int page, int taille) {
        Session qui = session.ouvrir(centreDemande);
        PaginationDuJournal pagination = PaginationDuJournal.de(page, taille);
        PagedResult<NotificationDuJournal> notifications = journal.lister(qui.centre(), qui.utilisateur(), qui.roles(),
                horloge.maintenant().minus(DureesDuJournal.FENETRE_DE_CONSULTATION), pagination.page(), pagination.taille());
        return new Consultation(qui.centre(), notifications);
    }

    public record Consultation(CentreId centre, PagedResult<NotificationDuJournal> notifications) {
    }
}
