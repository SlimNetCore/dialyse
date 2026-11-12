package com.hemodialyse.backend.contexts.notifications.interfaces.publie;

import com.hemodialyse.backend.contexts.notifications.application.NotificationsDuJournal;
import com.hemodialyse.backend.contexts.notifications.domain.Audience;
import com.hemodialyse.backend.contexts.notifications.domain.Notification;
import com.hemodialyse.backend.contexts.notifications.domain.NotificationId;
import com.hemodialyse.backend.contexts.notifications.domain.TypeDeNotification;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.JournalDesNotifications;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

/**
 * Le journal vu des émetteurs de la plateforme (transitoire). L'audience est lue dans les clés {@code targetRoles} (rôles séparés par des
 * virgules, vide : tous) et {@code targetUserId} du contenu, convention que les émetteurs actuels et le front partagent.
 */
public class JournalDesNotificationsPublie implements JournalDesNotifications {

    private static final String CLE_DES_ROLES = "targetRoles";
    private static final String CLE_DU_DESTINATAIRE = "targetUserId";

    private final NotificationsDuJournal journal;

    public JournalDesNotificationsPublie(NotificationsDuJournal journal) {
        this.journal = journal;
    }

    @Override
    public void enregistrer(UUID id, UUID centre, String type, Map<String, String> contenu, Instant creeLe) {
        String roles = contenu.getOrDefault(CLE_DES_ROLES, "");
        Audience audience = Audience.desRolesEtUnDestinataire(Arrays.asList((roles == null ? "" : roles).split(",")),
                contenu.get(CLE_DU_DESTINATAIRE));
        journal.enregistrer(Notification.emise(new NotificationId(id), new CentreId(centre), new TypeDeNotification(type), contenu, audience, creeLe));
    }

    @Override
    public boolean existe(UUID id) {
        return journal.existe(new NotificationId(id));
    }
}
