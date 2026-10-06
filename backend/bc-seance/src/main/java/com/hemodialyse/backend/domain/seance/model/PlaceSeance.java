package com.hemodialyse.backend.domain.seance.model;

import java.util.UUID;

/**
 * Place où une séance a eu lieu (salle, créneau, générateur), figée à sa validation : le planning de la semaine la
 * retrouve même si la place ou les jours de dialyse du patient changent ensuite.
 *
 * @param generateurId générateur, facultatif (patient placé sans générateur)
 */
public record PlaceSeance(UUID salleId, UUID creneauId, UUID generateurId) {

    public PlaceSeance {
        if (salleId == null || creneauId == null) {
            throw new IllegalArgumentException("La place d'une séance exige une salle et un créneau");
        }
    }
}
