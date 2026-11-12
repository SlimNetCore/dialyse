package com.hemodialyse.backend.contexts.notifications.application;

import com.hemodialyse.backend.contexts.notifications.domain.NotificationId;
import com.hemodialyse.backend.contexts.notifications.domain.NotificationInvalide;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Marquer des notifications lues par l'utilisateur de la session. Seules celles du centre ET dont l'audience l'inclut sont marquées (KB-NOT1-3
 * corrigé) ; les autres, comme les identifiants inconnus, sont ignorées sans rien révéler. Idempotent.
 */
public class MarquerLesNotificationsLues {

    private final SessionCourante session;
    private final NotificationsDuJournal journal;
    private final Horloge horloge;

    public MarquerLesNotificationsLues(SessionCourante session, NotificationsDuJournal journal, Horloge horloge) {
        this.session = session;
        this.journal = journal;
        this.horloge = horloge;
    }

    public void executer(UUID centreDemande, List<UUID> identifiants) {
        Session qui = session.ouvrir(centreDemande);
        if (identifiants == null) throw new NotificationInvalide("Les identifiants des notifications à marquer sont requis");
        List<NotificationId> ids = identifiants.stream().filter(Objects::nonNull).distinct().map(NotificationId::new).toList();
        journal.marquerLues(qui.centre(), qui.utilisateur(), qui.roles(), ids, horloge.maintenant());
    }
}
