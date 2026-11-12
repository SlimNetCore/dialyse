package com.hemodialyse.backend.contexts.notifications.publishedlanguage;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Le journal des notifications d'un centre, vu des émetteurs de la plateforme (transitoire, jusqu'à ce que {@link Notifier} les remplace,
 * NOT2 et PL13) : enregistrer une notification déjà composée et savoir si un identifiant déterministe y figure.
 */
public interface JournalDesNotifications {

    /**
     * Enregistre au journal. L'audience est lue dans le contenu : clés {@code targetRoles} (rôles séparés par des virgules, vide : tous)
     * et {@code targetUserId} (destinataire visé).
     */
    void enregistrer(UUID id, UUID centre, String type, Map<String, String> contenu, Instant creeLe);

    boolean existe(UUID id);
}
