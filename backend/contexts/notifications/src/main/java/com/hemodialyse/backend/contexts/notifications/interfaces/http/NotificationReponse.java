package com.hemodialyse.backend.contexts.notifications.interfaces.http;

import com.hemodialyse.backend.contexts.notifications.application.NotificationDuJournal;

import java.util.Map;
import java.util.UUID;

/**
 * Même forme qu'un évènement temps réel ({@code type}, {@code payload}, {@code timestamp}), avec l'identifiant et l'état de lecture. Les noms
 * de champs JSON sont ceux que le front lit depuis toujours (ex {@code AlerteResponse}) : ils ne suivent pas le glossaire.
 */
public record NotificationReponse(UUID id, String type, UUID centerId, Map<String, String> payload, String timestamp, boolean lue) {

    static NotificationReponse de(NotificationDuJournal notification, UUID centre) {
        return new NotificationReponse(notification.id().valeur(), notification.type().valeur(), centre, notification.contenu(),
                notification.creeLe().toString(), notification.lue());
    }
}
