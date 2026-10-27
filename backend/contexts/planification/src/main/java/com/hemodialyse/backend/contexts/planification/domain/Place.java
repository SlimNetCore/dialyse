package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Place d'un patient : une salle, un créneau, un générateur et au moins un jour de dialyse, tous présents (RG-PLN-020, 050). Un
 * patient garde la même place tous ses jours. Le patient sans place n'a pas de {@code Place} (absence, pas de valeur factice).
 */
public record Place(SalleId salle, CreneauId creneau, GenerateurId generateur, Set<JourDeLaSemaine> jours) {

    public Place {
        if (salle == null || creneau == null || generateur == null || jours == null || jours.isEmpty()) {
            throw new RegleDePlanificationViolee("PLACEMENT_INCOMPLET",
                    "Placement refusé par la planification : salle, créneau, générateur et jours sont requis ensemble");
        }
        jours = Collections.unmodifiableSet(EnumSet.copyOf(jours));
    }

    /**
     * Place demandée sur une fiche : rien du tout = pas de place (acceptée, RG-PLN-051) ; une partie seulement =
     * {@code PLACEMENT_INCOMPLET}.
     */
    public static Optional<Place> demandee(SalleId salle, CreneauId creneau, GenerateurId generateur, Set<JourDeLaSemaine> jours) {
        boolean aucun = salle == null && creneau == null && generateur == null && (jours == null || jours.isEmpty());
        return aucun ? Optional.empty() : Optional.of(new Place(salle, creneau, generateur, jours));
    }
}
