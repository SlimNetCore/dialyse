package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.application.InstantanesMensuels.InstantaneListe;

import java.util.List;
import java.util.UUID;

/** Les mois figés de la société, du plus récent au plus ancien. */
public final class ListerLesInstantanes {

    private final InstantanesMensuels instantanes;

    public ListerLesInstantanes(InstantanesMensuels instantanes) {
        this.instantanes = instantanes;
    }

    public List<InstantaneListe> execute(UUID societeId) {
        return instantanes.lister(societeId);
    }
}
