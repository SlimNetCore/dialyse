package com.hemodialyse.backend.contexts.notifications.domain;

/** Code du type choisi et documenté par l'émetteur ; Notifications ne l'interprète pas (les codes existants sont conservés tels quels). */
public record TypeDeNotification(String valeur) {
    public TypeDeNotification {
        if (valeur == null || valeur.isBlank()) throw new NotificationInvalide("Type de notification requis");
    }
}
