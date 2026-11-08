package com.hemodialyse.backend.contexts.seance.domain;

import java.util.UUID;

/**
 * Place où la séance a eu lieu (salle, créneau, générateur facultatif), figée à sa validation : le planning de la semaine la retrouve même
 * si la place ou les jours de dialyse du patient changent ensuite.
 */
public record PlaceDeSeance(UUID salle, UUID creneau, UUID generateur) {

    public PlaceDeSeance {
        if (salle == null || creneau == null) throw new SaisieInvalide("La place d'une séance exige une salle et un créneau");
    }
}
