package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.AffectationId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Une affectation du roulement : l'infirmier travaille dans une salle, sur un créneau, certains jours de la semaine (au moins un).
 * Entité interne de {@link RoulementDeLInfirmier} ; jamais écrite de l'extérieur.
 */
public record Affectation(AffectationId id, SalleId salle, CreneauId creneau, Set<JourDeLaSemaine> jours) {

    public Affectation {
        if (id == null || salle == null || creneau == null) {
            throw new SaisieInvalide("Affectation incomplète");
        }
        if (jours == null || jours.isEmpty()) {
            throw new SaisieInvalide("Au moins un jour de travail est requis");
        }
        jours = Collections.unmodifiableSet(EnumSet.copyOf(jours));
    }

    /**
     * Deux affectations distinctes se chevauchent si elles occupent le même créneau un même jour, quelle que soit la salle.
     */
    boolean chevauche(Affectation autre) {
        return !id.equals(autre.id) && creneau.equals(autre.creneau) && jours.stream().anyMatch(autre.jours::contains);
    }
}
