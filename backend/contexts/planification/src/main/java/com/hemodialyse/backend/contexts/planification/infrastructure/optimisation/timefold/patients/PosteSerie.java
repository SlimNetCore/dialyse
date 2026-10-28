package com.hemodialyse.backend.contexts.planification.infrastructure.optimisation.timefold.patients;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.Poste;

import java.util.UUID;

/**
 * Valeur du placement : un générateur sur un créneau (série). La salle se déduit du générateur.
 */
public record PosteSerie(UUID generateurId, String generateurCode, UUID salleId, UUID creneauId, boolean isolement) {

    public Poste versDomaine() {
        return new Poste(new SalleId(salleId), new CreneauId(creneauId), new GenerateurId(generateurId), generateurCode);
    }
}
