package com.hemodialyse.backend.infrastructure.optimisation.timefold.infirmiers;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;
import ai.timefold.solver.core.api.score.stream.uni.UniConstraintStream;

import static ai.timefold.solver.core.api.score.stream.ConstraintCollectors.count;
import static ai.timefold.solver.core.api.score.stream.ConstraintCollectors.countDistinct;
import static com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation.AFFINITE;
import static com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation.COMPETENCE;
import static com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation.CONTINUITE_SALLE;
import static com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation.DEPASSEMENT_HEBDOMADAIRE;
import static com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation.DOUBLE_VACATION;
import static com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation.EQUITE_CHARGE;
import static com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation.INFIRMIERS_MOBILISES;
import static com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation.MAX_VACATIONS_JOUR;
import static com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation.QUALIFICATION;
import static com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation.QUOTA_HEURES;
import static com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation.REPOS_HEBDOMADAIRE;
import static com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation.STABILITE_ROULEMENT;
import static com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation.VACATION_DOUBLE_CRENEAU;
import static com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation.VACATION_NON_POURVUE;

/**
 * Contraintes de la planification des infirmiers. Dures : un infirmier n'est jamais sur deux salles au même créneau
 * ({@code RG-INF-022}) et ne dépasse pas ses vacations quotidiennes. Moyenne : chaque vacation exigée est pourvue.
 * Souples : dépassement hebdomadaire, équité (ou économie de personnel), double vacation, continuité de salle,
 * connaissance de la salle et du créneau, stabilité du roulement, qualification, repos hebdomadaire, quota d'heures
 * (temps partiel) et compétences demandées par les patients de la case.
 * <p>
 * L'absence d'un infirmier, son habilitation à l'isolement et sa disponibilité sur le créneau sont garanties par la
 * liste de candidats de chaque vacation : elles ne sont pas des contraintes.
 */
public class InfirmiersConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[]{
                vacationDoubleCreneau(factory),
                maxVacationsJour(factory),
                vacationNonPourvue(factory),
                depassementHebdomadaire(factory),
                equiteCharge(factory),
                infirmiersMobilises(factory),
                doubleVacation(factory),
                continuiteSalle(factory),
                affinite(factory),
                stabiliteRoulement(factory),
                qualification(factory),
                reposHebdomadaire(factory),
                quotaHeures(factory),
                competence(factory)
        };
    }

    public Constraint vacationDoubleCreneau(ConstraintFactory factory) {
        return factory.forEachUniquePair(Vacation.class,
                        Joiners.equal(Vacation::getInfirmier),
                        Joiners.equal(Vacation::getDate),
                        Joiners.equal(Vacation::getCreneauId))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint(VACATION_DOUBLE_CRENEAU);
    }

    public Constraint maxVacationsJour(ConstraintFactory factory) {
        return factory.forEach(Vacation.class)
                .groupBy(Vacation::getInfirmier, Vacation::getDate, count())
                .filter((infirmier, date, n) -> n > infirmier.maxParJour())
                .penalize(HardMediumSoftScore.ONE_HARD, (infirmier, date, n) -> n.intValue() - infirmier.maxParJour())
                .asConstraint(MAX_VACATIONS_JOUR);
    }

    public Constraint vacationNonPourvue(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(Vacation.class)
                .filter(v -> v.getInfirmier() == null)
                .penalize(HardMediumSoftScore.ONE_MEDIUM)
                .asConstraint(VACATION_NON_POURVUE);
    }

    public Constraint depassementHebdomadaire(ConstraintFactory factory) {
        return factory.forEach(Vacation.class)
                .groupBy(Vacation::getInfirmier, Vacation::getSemaine, count())
                .filter((infirmier, semaine, n) -> n > infirmier.maxParSemaine())
                .penalize(HardMediumSoftScore.ONE_SOFT, (infirmier, semaine, n) -> n.intValue() - infirmier.maxParSemaine())
                .asConstraint(DEPASSEMENT_HEBDOMADAIRE);
    }

    /**
     * Somme des carrés des vacations par infirmier : à total égal, minimale quand la charge est également répartie.
     */
    public Constraint equiteCharge(ConstraintFactory factory) {
        return factory.forEach(Vacation.class)
                .groupBy(Vacation::getInfirmier, count())
                .penalize(HardMediumSoftScore.ONE_SOFT, (infirmier, n) -> (int) (n * n))
                .asConstraint(EQUITE_CHARGE);
    }

    public Constraint infirmiersMobilises(ConstraintFactory factory) {
        return factory.forEach(Vacation.class)
                .groupBy(Vacation::getInfirmier)
                .penalize(HardMediumSoftScore.ONE_SOFT)
                .asConstraint(INFIRMIERS_MOBILISES);
    }

    public Constraint doubleVacation(ConstraintFactory factory) {
        return factory.forEach(Vacation.class)
                .groupBy(Vacation::getInfirmier, Vacation::getDate, count())
                .filter((infirmier, date, n) -> n > 1)
                .penalize(HardMediumSoftScore.ONE_SOFT, (infirmier, date, n) -> n.intValue() - 1)
                .asConstraint(DOUBLE_VACATION);
    }

    /**
     * Un infirmier qui change de salle dans la semaine coûte plus qu'un infirmier qui reste dans la même salle.
     */
    public Constraint continuiteSalle(ConstraintFactory factory) {
        return factory.forEach(Vacation.class)
                .groupBy(Vacation::getInfirmier, countDistinct(Vacation::getSalleId))
                .filter((infirmier, salles) -> salles > 1)
                .penalize(HardMediumSoftScore.ONE_SOFT, (infirmier, salles) -> salles.intValue() - 1)
                .asConstraint(CONTINUITE_SALLE);
    }

    public Constraint affinite(ConstraintFactory factory) {
        return factory.forEach(Vacation.class)
                .filter(v -> v.getInfirmier().affiniteManquante(v.getSalleId(), v.getCreneauId()) > 0)
                .penalize(HardMediumSoftScore.ONE_SOFT,
                        v -> v.getInfirmier().affiniteManquante(v.getSalleId(), v.getCreneauId()))
                .asConstraint(AFFINITE);
    }

    public Constraint stabiliteRoulement(ConstraintFactory factory) {
        return factory.forEach(Vacation.class)
                .filter(v -> !v.getInfirmier().placesExactes().contains(v.cleExacte()))
                .penalize(HardMediumSoftScore.ONE_SOFT)
                .asConstraint(STABILITE_ROULEMENT);
    }

    public Constraint qualification(ConstraintFactory factory) {
        return factory.forEach(Vacation.class)
                .filter(v -> v.isPrefereQualifie() && v.getInfirmier().aideSoignant())
                .penalize(HardMediumSoftScore.ONE_SOFT)
                .asConstraint(QUALIFICATION);
    }

    /**
     * Repos hebdomadaire : au-delà de {@code joursTravailMax} jours travaillés dans la semaine, chaque jour de plus est
     * un repos manqué.
     */
    public Constraint reposHebdomadaire(ConstraintFactory factory) {
        return factory.forEach(Vacation.class)
                .groupBy(Vacation::getInfirmier, Vacation::getSemaine, countDistinct(Vacation::getDate))
                .filter((infirmier, semaine, jours) -> jours > infirmier.joursTravailMax())
                .penalize(HardMediumSoftScore.ONE_SOFT,
                        (infirmier, semaine, jours) -> jours.intValue() - infirmier.joursTravailMax())
                .asConstraint(REPOS_HEBDOMADAIRE);
    }

    /**
     * Quota d'heures hebdomadaire (temps partiel au prorata) : chaque heure au-delà est pénalisée.
     */
    public Constraint quotaHeures(ConstraintFactory factory) {
        return factory.forEach(Vacation.class)
                .groupBy(Vacation::getInfirmier, Vacation::getSemaine, count())
                .filter((infirmier, semaine, n) -> infirmier.heuresAuDela(n.intValue()) > 0)
                .penalize(HardMediumSoftScore.ONE_SOFT, (infirmier, semaine, n) -> infirmier.heuresAuDela(n.intValue()))
                .asConstraint(QUOTA_HEURES);
    }

    public Constraint competence(ConstraintFactory factory) {
        return competenceManquante(factory.forEach(ExigenceCompetence.class));
    }

    /**
     * Compétence demandée par un patient d'une case (pédiatrie, cathéter) qu'aucun infirmier de la case n'a.
     */
    public static Constraint competenceManquante(UniConstraintStream<ExigenceCompetence> exigences) {
        return exigences
                .ifNotExists(Vacation.class,
                        Joiners.equal(ExigenceCompetence::cle, Vacation::cleCase),
                        Joiners.filtering((e, v) -> v.getInfirmier() != null
                                && v.getInfirmier().competences().contains(e.competence())))
                .penalize(HardMediumSoftScore.ONE_SOFT)
                .asConstraint(COMPETENCE);
    }
}
