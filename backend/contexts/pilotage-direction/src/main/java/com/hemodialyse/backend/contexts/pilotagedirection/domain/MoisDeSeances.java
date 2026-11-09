package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import java.util.UUID;

/** Le nombre de séances réalisées par un centre pendant un mois civil. */
public record MoisDeSeances(UUID centreId, int annee, int mois, long nombre) {
}
