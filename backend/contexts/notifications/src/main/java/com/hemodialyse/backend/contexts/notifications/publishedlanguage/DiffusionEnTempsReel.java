package com.hemodialyse.backend.contexts.notifications.publishedlanguage;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Port FOURNI à Notifications par la plateforme : l'envoi immédiat aux utilisateurs connectés du centre (transport STOMP, destinations
 * inchangées). Notifications décide quoi diffuser et à quel moment ; le transport n'est pas son affaire.
 */
public interface DiffusionEnTempsReel {

    /**
     * @param identifiant identifiant de la notification au journal, ou {@code null} si elle n'y est pas enregistrée
     * @param contenu     contenu de l'émetteur, avec les clés {@code targetRoles} (et {@code targetUserId} d'une notification personnelle)
     */
    void diffuser(UUID centre, String type, Map<String, String> contenu, UUID identifiant, Instant emisLe);
}
