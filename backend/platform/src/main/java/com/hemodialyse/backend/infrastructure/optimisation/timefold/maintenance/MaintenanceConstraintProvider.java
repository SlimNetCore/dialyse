package com.hemodialyse.backend.infrastructure.optimisation.timefold.maintenance;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;

import static com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation.CHANGEMENT_CRENEAU_TEMPORAIRE;
import static com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation.CHANGEMENT_SALLE_TEMPORAIRE;
import static com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation.POSTE_TEMPORAIRE_DOUBLE;
import static com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation.SEANCE_SANS_SOLUTION;

/**
 * Contraintes des déplacements temporaires. Dure : deux séances déplacées ne prennent pas la même place le même jour
 * (les places des autres patients et les générateurs indisponibles sont déjà exclus des valeurs possibles). Moyenne :
 * chaque séance trouve une place. Souples : garder le créneau, puis la salle, du patient.
 */
public class MaintenanceConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[]{
                posteDouble(factory),
                seanceSansSolution(factory),
                changementCreneau(factory),
                changementSalle(factory)
        };
    }

    Constraint posteDouble(ConstraintFactory factory) {
        return factory.forEachUniquePair(SeanceTemporaire.class,
                        Joiners.equal(SeanceTemporaire::getDate),
                        Joiners.equal(SeanceTemporaire::getPoste))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint(POSTE_TEMPORAIRE_DOUBLE);
    }

    Constraint seanceSansSolution(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(SeanceTemporaire.class)
                .filter(s -> s.getPoste() == null)
                .penalize(HardMediumSoftScore.ONE_MEDIUM)
                .asConstraint(SEANCE_SANS_SOLUTION);
    }

    Constraint changementCreneau(ConstraintFactory factory) {
        return factory.forEach(SeanceTemporaire.class)
                .filter(SeanceTemporaire::changeDeCreneau)
                .penalize(HardMediumSoftScore.ONE_SOFT)
                .asConstraint(CHANGEMENT_CRENEAU_TEMPORAIRE);
    }

    Constraint changementSalle(ConstraintFactory factory) {
        return factory.forEach(SeanceTemporaire.class)
                .filter(SeanceTemporaire::changeDeSalle)
                .penalize(HardMediumSoftScore.ONE_SOFT)
                .asConstraint(CHANGEMENT_SALLE_TEMPORAIRE);
    }
}
