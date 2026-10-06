package com.hemodialyse.backend.infrastructure.optimisation.timefold.patients;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;
import ai.timefold.solver.core.api.score.stream.bi.BiConstraintStream;
import com.hemodialyse.backend.domain.infirmier.service.PresenceInfirmierService;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.CleCase;

import java.util.UUID;

import static ai.timefold.solver.core.api.score.stream.ConstraintCollectors.countBi;
import static com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation.CHANGEMENT_JOURS;
import static com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation.CRENEAU_PREFERE;
import static com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation.ESPACEMENT_JOURS;
import static com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation.GENERATEURS_UTILISES;
import static com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation.GENERATEUR_DOUBLE;
import static com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation.INFIRMIERS_REQUIS;
import static com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation.ISOLEMENT;
import static com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation.PATIENT_NON_PLACE;
import static com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation.RESERVE_SECOURS;
import static com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation.SALLES_OUVERTES;
import static com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation.STABILITE_PATIENTS;
import static com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation.TRANSPORT_PARTAGE;

/**
 * Contraintes du placement des patients. Dures : un générateur ne sert qu'un patient par jour et par créneau
 * ({@code RG-PLN-021}), l'isolement est respecté ({@code RG-PLN-024}). Moyenne : un patient doit être placé. Souples :
 * minimiser les vacations d'infirmiers exigées par le ratio, les salles ouvertes et les générateurs utilisés, garder la
 * réserve de secours, changer le moins possible les habitudes des patients (place et jours), bien espacer les jours
 * choisis, respecter le créneau préféré et regrouper au même créneau les patients d'un même transporteur.
 * <p>
 * Les poids sont unitaires ici ; les poids réels viennent de {@code PoidsOptimisation}. Les contraintes sont publiques :
 * le modèle conjoint patients + infirmiers les réutilise.
 */
public class PatientsConstraintProvider implements ConstraintProvider {

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
                stabilite(factory),
                espacementJours(factory),
                changementJours(factory),
                creneauPrefere(factory),
                transportPartage(factory)
        };
    }

    /**
     * Chaque (patient, jour de dialyse) d'un patient placé.
     */
    public static BiConstraintStream<PlacementPatient, JourSemaine> seances(ConstraintFactory factory) {
        return factory.forEach(PlacementPatient.class)
                .expand(PlacementPatient::getJours).flattenLast(jours -> jours);
    }

    public static CleCase cle(PlacementPatient p, JourSemaine jour) {
        return new CleCase(p.getPoste().salleId(), p.getPoste().creneauId(), 0, jour);
    }

    public Constraint generateurDouble(ConstraintFactory factory) {
        return factory.forEachUniquePair(PlacementPatient.class,
                        Joiners.equal(PlacementPatient::getPoste))
                .filter(PlacementPatient::partageUnJour)
                .penalize(HardMediumSoftScore.ONE_HARD, PlacementPatient::joursCommuns)
                .asConstraint(GENERATEUR_DOUBLE);
    }

    public Constraint isolement(ConstraintFactory factory) {
        return factory.forEach(PlacementPatient.class)
                .filter(p -> p.getPoste().isolement() != p.isARisque())
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint(ISOLEMENT);
    }

    public Constraint patientNonPlace(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(PlacementPatient.class)
                .filter(p -> p.getPoste() == null)
                .penalize(HardMediumSoftScore.ONE_MEDIUM)
                .asConstraint(PATIENT_NON_PLACE);
    }

    /**
     * Une vacation d'infirmier par tranche de {@code ratio} patients et par case (salle, créneau, jour).
     */
    public Constraint infirmiersRequis(ConstraintFactory factory) {
        return seances(factory)
                .groupBy(PatientsConstraintProvider::cle, countBi())
                .join(ContexteCentre.class)
                .penalize(HardMediumSoftScore.ONE_SOFT,
                        (cle, patients, ctx) -> PresenceInfirmierService.requis(patients.intValue(), ctx.patientsParInfirmier()))
                .asConstraint(INFIRMIERS_REQUIS);
    }

    public Constraint sallesOuvertes(ConstraintFactory factory) {
        return seances(factory)
                .groupBy(PatientsConstraintProvider::cle, countBi())
                .penalize(HardMediumSoftScore.ONE_SOFT)
                .asConstraint(SALLES_OUVERTES);
    }

    public Constraint generateursUtilises(ConstraintFactory factory) {
        return factory.forEach(PlacementPatient.class)
                .groupBy(p -> p.getPoste().generateurId())
                .penalize(HardMediumSoftScore.ONE_SOFT)
                .asConstraint(GENERATEURS_UTILISES);
    }

    /**
     * Garde la réserve de générateurs de secours (1 pour 8) libre à chaque créneau et chaque jour.
     */
    public Constraint reserveSecours(ConstraintFactory factory) {
        return seances(factory)
                .groupBy((p, jour) -> new CreneauJourKey(p.getPoste().creneauId(), jour), countBi())
                .join(ContexteCentre.class)
                .filter((cle, utilises, ctx) -> utilises > ctx.generateursExploitables())
                .penalize(HardMediumSoftScore.ONE_SOFT,
                        (cle, utilises, ctx) -> utilises.intValue() - ctx.generateursExploitables())
                .asConstraint(RESERVE_SECOURS);
    }

    public Constraint stabilite(ConstraintFactory factory) {
        return factory.forEach(PlacementPatient.class)
                .filter(p -> p.coutChangement() > 0)
                .penalize(HardMediumSoftScore.ONE_SOFT, PlacementPatient::coutChangement)
                .asConstraint(STABILITE_PATIENTS);
    }

    /**
     * Jours choisis par l'optimisation : préférer le schéma le mieux espacé (même note que l'aide au placement).
     */
    public Constraint espacementJours(ConstraintFactory factory) {
        return factory.forEach(PlacementPatient.class)
                .filter(p -> p.penaliteEspacement() > 0)
                .penalize(HardMediumSoftScore.ONE_SOFT, PlacementPatient::penaliteEspacement)
                .asConstraint(ESPACEMENT_JOURS);
    }

    /**
     * Patient dont les jours sont revus : chaque jour actuel abandonné est un changement d'habitude.
     */
    public Constraint changementJours(ConstraintFactory factory) {
        return factory.forEach(PlacementPatient.class)
                .filter(p -> p.joursChanges() > 0)
                .penalize(HardMediumSoftScore.ONE_SOFT, PlacementPatient::joursChanges)
                .asConstraint(CHANGEMENT_JOURS);
    }

    public Constraint creneauPrefere(ConstraintFactory factory) {
        return factory.forEach(PlacementPatient.class)
                .filter(PlacementPatient::horsCreneauPrefere)
                .penalize(HardMediumSoftScore.ONE_SOFT)
                .asConstraint(CRENEAU_PREFERE);
    }

    /**
     * Deux patients d'un même transporteur qui dialysent le même jour à des créneaux différents imposent deux trajets.
     */
    public Constraint transportPartage(ConstraintFactory factory) {
        return factory.forEachUniquePair(PlacementPatient.class,
                        Joiners.filtering(PlacementPatient::partageUnTransporteur))
                .filter((a, b) -> !a.getPoste().creneauId().equals(b.getPoste().creneauId()) && a.partageUnJour(b))
                .penalize(HardMediumSoftScore.ONE_SOFT, PlacementPatient::joursCommuns)
                .asConstraint(TRANSPORT_PARTAGE);
    }
}
