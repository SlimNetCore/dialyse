package com.hemodialyse.backend.contexts.notifications.application;

import com.hemodialyse.backend.contexts.notifications.domain.Notification;
import com.hemodialyse.backend.contexts.notifications.domain.NotificationId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;

/**
 * Port sortant : le journal durable des notifications d'un centre et les lectures de chaque utilisateur. Toujours borné au centre ; la
 * visibilité suit l'audience (rôles comparés exactement, ou compte destinataire visé). Mêmes tests de contrat pour le double en mémoire et
 * l'adapter JDBC.
 */
public interface NotificationsDuJournal {

    void enregistrer(Notification notification);

    boolean existe(NotificationId id);

    /** Notifications du centre visibles par l'utilisateur, émises à partir de {@code depuis}, de la plus récente à la plus ancienne. */
    PagedResult<NotificationDuJournal> lister(CentreId centre, String utilisateur, Set<String> roles, Instant depuis, int page, int taille);

    /** Marque lues, pour cet utilisateur, celles des identifiants qui sont de ce centre ET de son audience ; les autres sont ignorées. */
    void marquerLues(CentreId centre, String utilisateur, Set<String> roles, Collection<NotificationId> ids, Instant quand);

    /** Marque lues toutes les notifications visibles du centre émises à partir de {@code depuis}. */
    void toutMarquerLu(CentreId centre, String utilisateur, Set<String> roles, Instant depuis, Instant quand);

    /** Supprime les notifications émises avant {@code avant} et leurs lectures, tous centres confondus. */
    int purger(Instant avant);
}
