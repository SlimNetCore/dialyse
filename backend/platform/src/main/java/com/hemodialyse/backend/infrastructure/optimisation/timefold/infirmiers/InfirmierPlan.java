package com.hemodialyse.backend.infrastructure.optimisation.timefold.infirmiers;

import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.optimisation.model.CompetenceInfirmier;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Infirmier candidat aux vacations : qualification, habilitation à l'isolement, limites de charge (vacations par jour et
 * par semaine, jours travaillés par semaine pour garantir le repos, quota d'heures au prorata du temps partiel),
 * compétences particulières et habitudes de son roulement actuel (salles et créneaux connus, places exactes).
 *
 * @param placesExactes     clés « salle|créneau|jour » de son roulement actuel
 * @param joursTravailMax   jours travaillés au plus par semaine (7 moins le repos hebdomadaire exigé)
 * @param heuresParVacation durée d'une vacation (heures)
 * @param quotaHeures       heures hebdomadaires autorisées
 */
public record InfirmierPlan(UUID id, String nom, boolean aideSoignant, boolean habiliteIsolement, int maxParJour,
                            int maxParSemaine, Set<UUID> sallesHabituelles, Set<UUID> creneauxHabituels,
                            Set<String> placesExactes, int joursTravailMax, int heuresParVacation, int quotaHeures,
                            Set<CompetenceInfirmier> competences) {

    public InfirmierPlan {
        sallesHabituelles = sallesHabituelles == null ? Set.of() : Set.copyOf(sallesHabituelles);
        creneauxHabituels = creneauxHabituels == null ? Set.of() : Set.copyOf(creneauxHabituels);
        placesExactes = placesExactes == null ? Set.of() : Set.copyOf(placesExactes);
        competences = competences == null ? Set.of() : Set.copyOf(competences);
    }

    /**
     * Infirmier à temps plein, sans compétence particulière ni contrainte de repos ou d'heures.
     */
    public InfirmierPlan(UUID id, String nom, boolean aideSoignant, boolean habiliteIsolement, int maxParJour,
                         int maxParSemaine, Set<UUID> sallesHabituelles, Set<UUID> creneauxHabituels,
                         Set<String> placesExactes) {
        this(id, nom, aideSoignant, habiliteIsolement, maxParJour, maxParSemaine, sallesHabituelles, creneauxHabituels,
                placesExactes, JourSemaine.NB_JOURS, 0, Integer.MAX_VALUE, Set.of());
    }

    /**
     * Nombre d'habitudes que cette case ne respecte pas : salle inconnue (1) et créneau inhabituel (1).
     */
    public int affiniteManquante(UUID salleId, UUID creneauId) {
        return (sallesHabituelles.contains(salleId) ? 0 : 1) + (creneauxHabituels.contains(creneauId) ? 0 : 1);
    }

    /**
     * Heures au-delà du quota pour {@code vacations} vacations dans la semaine (0 si le quota est tenu).
     */
    public int heuresAuDela(int vacations) {
        long heures = (long) vacations * heuresParVacation;
        return heures > quotaHeures ? (int) (heures - quotaHeures) : 0;
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
