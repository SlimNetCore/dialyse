package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.application.InstantanesMensuels.InstantaneListe;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.MoisDInstantane;

import java.util.List;
import java.util.UUID;

/** Les mois figés de la société, du plus récent au plus ancien. */
public final class ListerLesInstantanes {

    private final InstantanesMensuels instantanes;

    public ListerLesInstantanes(InstantanesMensuels instantanes) {
        this.instantanes = instantanes;
    }

    /** KB-PL10a-4 corrigé (2026-11-12) : une ligne dont le mois n'est pas un mois {@code AAAA-MM} lisible est ignorée (jamais supprimée de la base). */
    public List<InstantaneListe> execute(UUID societeId) {
        return instantanes.lister(societeId).stream().filter(i -> MoisDInstantane.estLisible(i.mois())).toList();
    }
}
