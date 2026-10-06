package com.hemodialyse.backend.infrastructure.optimisation.timefold.patients;

import com.hemodialyse.backend.domain.planning.optimisation.model.Poste;

import java.util.UUID;

/**
 * Valeur du placement : un générateur sur un créneau (série). La salle se déduit du générateur.
 */
public record PosteSerie(UUID generateurId, String generateurCode, UUID salleId, UUID creneauId, boolean isolement) {

    public Poste versDomaine() {
        return new Poste(salleId, creneauId, generateurId, generateurCode);
    }
}
