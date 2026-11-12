package com.hemodialyse.backend.contexts.notifications.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Message adressé aux personnes d'un centre pour les prévenir d'un fait métier, conservé au journal. Le contenu (paires de texte) et le type
 * sont ceux de l'émetteur ; la visibilité dépend de l'audience. La lecture est propre à chaque utilisateur et vit hors de la notification.
 */
public record Notification(NotificationId id, CentreId centre, TypeDeNotification type, Map<String, String> contenu, Audience audience,
                           Instant creeLe) {

    public Notification {
        if (id == null) throw new NotificationInvalide("Identifiant de notification requis");
        if (centre == null) throw new NotificationInvalide("Une notification est rattachée à un centre");
        if (type == null) throw new NotificationInvalide("Type de notification requis");
        if (audience == null) throw new NotificationInvalide("Audience requise");
        if (creeLe == null) throw new NotificationInvalide("Date d'émission requise");
        contenu = contenu == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(contenu));
    }

    public static Notification emise(NotificationId id, CentreId centre, TypeDeNotification type, Map<String, String> contenu,
                                     Audience audience, Instant creeLe) {
        return new Notification(id, centre, type, contenu, audience, creeLe);
    }

    public boolean estVisiblePar(Set<String> rolesDeLUtilisateur, String utilisateur) {
        return audience.inclut(rolesDeLUtilisateur, utilisateur);
    }
}
