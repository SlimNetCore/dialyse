package com.hemodialyse.backend.contexts.gmao.application;

import java.time.Instant;

/** Port sortant : l'heure courante (UTC). Substituable par un double déterministe dans les tests. */
public interface Horloge {
    Instant maintenant();
}
