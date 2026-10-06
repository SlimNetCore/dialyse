package com.hemodialyse.backend.infrastructure.optimisation.timefold;

import com.hemodialyse.backend.domain.planning.model.JourSemaine;

import java.util.UUID;

/**
 * Case du planning où des patients dialysent et où des infirmiers tiennent une vacation : une salle, un créneau, un jour
 * d'une semaine de l'horizon (0 pour la semaine type). Clé commune aux patients et aux vacations.
 */
public record CleCase(UUID salleId, UUID creneauId, int semaine, JourSemaine jour) {
}
