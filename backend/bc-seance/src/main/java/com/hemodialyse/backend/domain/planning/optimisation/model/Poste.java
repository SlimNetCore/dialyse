package com.hemodialyse.backend.domain.planning.optimisation.model;

import java.util.UUID;

/**
 * Place de dialyse d'un patient : salle, créneau (série) et générateur.
 */
public record Poste(UUID salleId, UUID creneauId, UUID generateurId, String generateurCode) {

    public boolean memePlaceQue(Poste autre) {
        return autre != null && java.util.Objects.equals(salleId, autre.salleId)
                && java.util.Objects.equals(creneauId, autre.creneauId)
                && java.util.Objects.equals(generateurId, autre.generateurId);
    }
}
