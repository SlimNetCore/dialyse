package com.hemodialyse.backend.contexts.notifications.application;

import com.hemodialyse.backend.contexts.notifications.domain.Audience;

import java.util.Map;
import java.util.UUID;

/** Ce qu'un émetteur demande de notifier (centre, type, contenu, audience), dans le vocabulaire du contexte. */
public record Emission(UUID centre, String type, Map<String, String> contenu, Audience audience, UUID identifiantDeterministe,
                       boolean journalisee) {
}
