package com.hemodialyse.backend.contexts.notifications.application;

import java.time.Instant;

/** Port sortant : l'instant présent (fenêtre de consultation, date de lecture, durée de conservation). */
public interface Horloge {
    Instant maintenant();
}
