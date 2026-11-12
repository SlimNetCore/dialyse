package com.hemodialyse.backend.contexts.notifications.application;

import com.hemodialyse.backend.contexts.notifications.domain.DureesDuJournal;

import java.time.Instant;
import java.util.UUID;

/** Tout marquer lu : toutes les notifications récentes (30 jours) du centre visibles par l'utilisateur ; respecte l'audience, idempotent. */
public class ToutMarquerLu {

    private final SessionCourante session;
    private final NotificationsDuJournal journal;
    private final Horloge horloge;

    public ToutMarquerLu(SessionCourante session, NotificationsDuJournal journal, Horloge horloge) {
        this.session = session;
        this.journal = journal;
        this.horloge = horloge;
    }

    public void executer(UUID centreDemande) {
        Session qui = session.ouvrir(centreDemande);
        Instant maintenant = horloge.maintenant();
        journal.toutMarquerLu(qui.centre(), qui.utilisateur(), qui.roles(), maintenant.minus(DureesDuJournal.FENETRE_DE_CONSULTATION), maintenant);
    }
}
