package com.hemodialyse.backend.infrastructure.optimisation.timefold.patients;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;
import com.hemodialyse.backend.domain.infirmier.service.PresenceInfirmierService;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;

import java.util.UUID;

import static ai.timefold.solver.core.api.score.stream.ConstraintCollectors.countBi;
import static com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation.GENERATEURS_UTILISES;
import static com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation.GENERATEUR_DOUBLE;
import static com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation.INFIRMIERS_REQUIS;
import static com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation.ISOLEMENT;
import static com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation.PATIENT_NON_PLACE;
import static com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation.RESERVE_SECOURS;
import static com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation.SALLES_OUVERTES;
import static com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation.STABILITE_PATIENTS;

/**
 * Contraintes du placement des patients. Dures : un générateur ne sert qu'un patient par jour et par créneau
 * ({@code RG-PLN-021}), l'isolement est respecté ({@code RG-PLN-024}). Moyenne : un patient doit être placé. Souples :
 * minimiser les vacations d'infirmiers exigées par le ratio, les salles ouvertes et les générateurs utilisés, garder la
 * réserve de secours et changer le moins possible les habitudes des patients.
 * <p>
 * Les poids sont unitaires ici ; les poids réels viennent de {@code PoidsOptimisation}.
 */
public class PatientsConstraintProvider implements ConstraintProvider {

    /**
     * Case où des patients dialysent : une salle, un créneau, un jour.
     */
    record CaseKey(UUID salleId, UUID creneauId, JourSemaine jour) {
    }

    record CreneauJourKey(UUID creneauId, JourSemaine jour) {
    }

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[]{
                generateurDouble(factory),
                isolement(factory),
                patientNonPlace(factory),
                infirmiersRequis(factory),
                sallesOuvertes(factory),
                generateursUtilises(factory),
                reserveSecours(factory),
                stabilite(factory)
        };
    }

    Constraint generateurDouble(ConstraintFactory factory) {
        return factory.forEachUniquePair(PlacementPatient.class,
                        Joiners.equal(PlacementPatient::getPoste))
                .filter(PlacementPatient::partageUnJour)
                .penalize(HardMediumSoftScore.ONE_HARD,
                        (a, b) -> Integer.bitCount(a.getJoursMask() & b.getJoursMask()))
                .asConstraint(GENERATEUR_DOUBLE);
    }

    Constraint isolement(ConstraintFactory factory) {
        return factory.forEach(PlacementPatient.class)
                .filter(p -> p.getPoste().isolement() != p.isARisque())
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint(ISOLEMENT);
    }

    Constraint patientNonPlace(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(PlacementPatient.class)
                .filter(p -> p.getPoste() == null)
                .penalize(HardMediumSoftScore.ONE_MEDIUM)
                .asConstraint(PATIENT_NON_PLACE);
    }

    /**
     * Une vacation d'infirmier par tranche de {@code ratio} patients et par case (salle, créneau, jour).
     */
    Constraint infirmiersRequis(ConstraintFactory factory) {
        return factory.forEach(PlacementPatient.class)
                .expand(PlacementPatient::getJours).flattenLast(jours -> jours)
                .groupBy((p, jour) -> new CaseKey(p.getPoste().salleId(), p.getPoste().creneauId(), jour), countBi())
                .join(ContexteCentre.class)
                .penalize(HardMediumSoftScore.ONE_SOFT,
                        (cle, patients, ctx) -> PresenceInfirmierService.requis(patients.intValue(), ctx.patientsParInfirmier()))
                .asConstraint(INFIRMIERS_REQUIS);
    }

    Constraint sallesOuvertes(ConstraintFactory factory) {
        return factory.forEach(PlacementPatient.class)
                .expand(PlacementPatient::getJours).flattenLast(jours -> jours)
                .groupBy((p, jour) -> new CaseKey(p.getPoste().salleId(), p.getPoste().creneauId(), jour), countBi())
                .penalize(HardMediumSoftScore.ONE_SOFT)
                .asConstraint(SALLES_OUVERTES);
    }

    Constraint generateursUtilises(ConstraintFactory factory) {
        return factory.forEach(PlacementPatient.class)
                .groupBy(p -> p.getPoste().generateurId())
                .penalize(HardMediumSoftScore.ONE_SOFT)
                .asConstraint(GENERATEURS_UTILISES);
    }

    /**
     * Garde la réserve de générateurs de secours (1 pour 8) libre à chaque créneau et chaque jour.
     */
    Constraint reserveSecours(ConstraintFactory factory) {
        return factory.forEach(PlacementPatient.class)
                .expand(PlacementPatient::getJours).flattenLast(jours -> jours)
                .groupBy((p, jour) -> new CreneauJourKey(p.getPoste().creneauId(), jour), countBi())
                .join(ContexteCentre.class)
                .filter((cle, utilises, ctx) -> utilises > ctx.generateursExploitables())
                .penalize(HardMediumSoftScore.ONE_SOFT,
                        (cle, utilises, ctx) -> utilises.intValue() - ctx.generateursExploitables())
                .asConstraint(RESERVE_SECOURS);
    }

    Constraint stabilite(ConstraintFactory factory) {
        return factory.forEach(PlacementPatient.class)
                .filter(p -> p.coutChangement() > 0)
                .penalize(HardMediumSoftScore.ONE_SOFT, PlacementPatient::coutChangement)
                .asConstraint(STABILITE_PATIENTS);
    }
}
