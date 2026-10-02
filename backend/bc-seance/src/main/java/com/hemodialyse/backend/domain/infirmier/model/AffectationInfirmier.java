package com.hemodialyse.backend.domain.infirmier.model;

import com.hemodialyse.backend.domain.planning.model.JourSemaine;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Roulement d'un infirmier : il travaille dans une salle, sur un créneau (position horaire), certains jours de la
 * semaine. Un infirmier peut avoir plusieurs affectations, mais jamais deux sur le même créneau le même jour.
 */
public record AffectationInfirmier(
        UUID id,
        UUID centerId,
        UUID infirmierId,
        UUID salleId,
        UUID creneauId,
        Set<JourSemaine> jours
) {
    public AffectationInfirmier {
        if (centerId == null) throw new IllegalArgumentException("Centre requis");
        if (infirmierId == null) throw new IllegalArgumentException("Infirmier requis");
        if (salleId == null) throw new IllegalArgumentException("Salle requise");
        if (creneauId == null) throw new IllegalArgumentException("Créneau requis");
        if (jours == null || jours.isEmpty())
            throw new IllegalArgumentException("Au moins un jour de travail est requis");
        jours = Collections.unmodifiableSet(EnumSet.copyOf(jours));
    }

    public static AffectationInfirmier creer(UUID centerId, UUID infirmierId, UUID salleId, UUID creneauId,
                                             Set<JourSemaine> jours) {
        return new AffectationInfirmier(UUID.randomUUID(), centerId, infirmierId, salleId, creneauId, jours);
    }

    public AffectationInfirmier modifier(UUID salleId, UUID creneauId, Set<JourSemaine> jours) {
        return new AffectationInfirmier(id, centerId, infirmierId, salleId, creneauId, jours);
    }

    /**
     * Vrai si les deux affectations placent le même infirmier sur le même créneau un même jour.
     */
    public boolean chevauche(AffectationInfirmier autre) {
        return !id.equals(autre.id) && infirmierId.equals(autre.infirmierId) && creneauId.equals(autre.creneauId)
                && jours.stream().anyMatch(autre.jours::contains);
    }
}
