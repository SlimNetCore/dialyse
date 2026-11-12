package com.hemodialyse.backend.contexts.notifications.domain;

import java.util.UUID;

/** Identité d'une notification du journal (aléatoire, ou déterministe quand l'émetteur veut qu'un fait rejoué ne crée pas de doublon). */
public record NotificationId(UUID valeur) {
    public NotificationId {
        if (valeur == null) throw new NotificationInvalide("Identifiant de notification requis");
    }
}
