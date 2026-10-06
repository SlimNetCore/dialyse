package com.hemodialyse.backend.infrastructure.optimisation.timefold.infirmiers;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Infirmier candidat aux vacations : qualification, habilitation à l'isolement, limites de charge et habitudes de son
 * roulement actuel (salles et créneaux connus, places exactes).
 *
 * @param placesExactes clés « salle|créneau|jour » de son roulement actuel
 */
public record InfirmierPlan(UUID id, String nom, boolean aideSoignant, boolean habiliteIsolement, int maxParJour,
                            int maxParSemaine, Set<UUID> sallesHabituelles, Set<UUID> creneauxHabituels,
                            Set<String> placesExactes) {

    public InfirmierPlan {
        sallesHabituelles = sallesHabituelles == null ? Set.of() : Set.copyOf(sallesHabituelles);
        creneauxHabituels = creneauxHabituels == null ? Set.of() : Set.copyOf(creneauxHabituels);
        placesExactes = placesExactes == null ? Set.of() : Set.copyOf(placesExactes);
    }

    /**
     * Nombre d'habitudes que cette case ne respecte pas : salle inconnue (1) et créneau inhabituel (1).
     */
    public int affiniteManquante(UUID salleId, UUID creneauId) {
        return (sallesHabituelles.contains(salleId) ? 0 : 1) + (creneauxHabituels.contains(creneauId) ? 0 : 1);
    }

    @Override
    public boolean equals(Object autre) {
        return autre instanceof InfirmierPlan i && id.equals(i.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
