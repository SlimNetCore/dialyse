package com.hemodialyse.backend.infrastructure.optimisation.timefold.complet;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.uni.UniConstraintStream;
import com.hemodialyse.backend.domain.infirmier.service.PresenceInfirmierService;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.CleCase;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.infirmiers.ExigenceCompetence;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.infirmiers.InfirmiersConstraintProvider;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.infirmiers.Vacation;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.patients.ContexteCentre;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.patients.PatientsConstraintProvider;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.patients.PlacementPatient;

import static ai.timefold.solver.core.api.score.stream.ConstraintCollectors.countBi;
import static ai.timefold.solver.core.api.score.stream.ConstraintCollectors.sum;
import static com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation.QUALIFICATION;
import static com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation.VACATION_INUTILE;
import static com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation.VACATION_NON_POURVUE;

/**
 * Contraintes du modèle conjoint : toutes celles du placement des patients et du roulement des infirmiers, le besoin en
 * infirmiers d'une case étant recalculé à partir des patients qui y sont placés. Moyenne : chaque vacation exigée par
 * le ratio est tenue. Souples : pas de vacation tenue sans patient à suivre, un infirmier qualifié par case servie, les
 * compétences demandées par les patients de la case.
 */
public class CompletConstraintProvider implements ConstraintProvider {

    private final PatientsConstraintProvider patients = new PatientsConstraintProvider();
    private final InfirmiersConstraintProvider infirmiers = new InfirmiersConstraintProvider();

    /**
     * Contribution d'une case : vacations exigées par ses patients ou vacation tenue par un infirmier.
     */
    record Bilan(CleCase cle, int demande, int offre) {
    }

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[]{
                patients.generateurDouble(factory),
                patients.isolement(factory),
                patients.patientNonPlace(factory),
                patients.infirmiersRequis(factory),
                patients.sallesOuvertes(factory),
                patients.generateursUtilises(factory),
                patients.reserveSecours(factory),
                patients.stabilite(factory),
                patients.espacementJours(factory),
                patients.changementJours(factory),
                patients.creneauPrefere(factory),
                patients.transportPartage(factory),
                infirmiers.vacationDoubleCreneau(factory),
                infirmiers.maxVacationsJour(factory),
                infirmiers.depassementHebdomadaire(factory),
                infirmiers.equiteCharge(factory),
                infirmiers.infirmiersMobilises(factory),
                infirmiers.doubleVacation(factory),
                infirmiers.continuiteSalle(factory),
                infirmiers.affinite(factory),
                infirmiers.stabiliteRoulement(factory),
                infirmiers.reposHebdomadaire(factory),
                infirmiers.quotaHeures(factory),
                couvertureManquante(factory),
                vacationInutile(factory),
                qualificationCase(factory),
                competence(factory)
        };
    }

    private static UniConstraintStream<Bilan> bilans(ConstraintFactory factory) {
        UniConstraintStream<Bilan> demande = PatientsConstraintProvider.seances(factory)
                .groupBy(PatientsConstraintProvider::cle, countBi())
                .join(ContexteCentre.class)
                .map((cle, n, ctx) -> new Bilan(cle, PresenceInfirmierService.requis(n.intValue(),
                        ctx.patientsParInfirmier()), 0));
        UniConstraintStream<Bilan> offre = factory.forEach(Vacation.class)
                .map(v -> new Bilan(v.cleCase(), 0, 1));
        return demande.concat(offre);
    }

    /**
     * Vacations exigées par les patients d'une case et qu'aucun infirmier ne tient.
     */
    Constraint couvertureManquante(ConstraintFactory factory) {
        return bilans(factory)
                .groupBy(Bilan::cle, sum(Bilan::demande), sum(Bilan::offre))
                .filter((cle, demande, offre) -> demande > offre)
                .penalize(HardMediumSoftScore.ONE_MEDIUM, (cle, demande, offre) -> demande - offre)
                .asConstraint(VACATION_NON_POURVUE);
    }

    /**
     * Vacations tenues au-delà du besoin de la case (ou sur une case sans patient) : du personnel mobilisé pour rien.
     */
    Constraint vacationInutile(ConstraintFactory factory) {
        return bilans(factory)
                .groupBy(Bilan::cle, sum(Bilan::demande), sum(Bilan::offre))
                .filter((cle, demande, offre) -> offre > demande)
                .penalize(HardMediumSoftScore.ONE_SOFT, (cle, demande, offre) -> offre - demande)
                .asConstraint(VACATION_INUTILE);
    }

    /**
     * Une case servie uniquement par des aides-soignants.
     */
    Constraint qualificationCase(ConstraintFactory factory) {
        return factory.forEach(Vacation.class)
                .groupBy(Vacation::cleCase, sum(v -> v.getInfirmier().aideSoignant() ? 0 : 1))
                .filter((cle, qualifies) -> qualifies == 0)
                .penalize(HardMediumSoftScore.ONE_SOFT)
                .asConstraint(QUALIFICATION);
    }

    Constraint competence(ConstraintFactory factory) {
        UniConstraintStream<ExigenceCompetence> exigences = factory.forEach(PlacementPatient.class)
                .filter(p -> !p.getCompetences().isEmpty())
                .expand(PlacementPatient::getJours).flattenLast(jours -> jours)
                .expand((p, jour) -> p.getCompetences()).flattenLast(competences -> competences)
                .map((p, jour, competence) -> new ExigenceCompetence(PatientsConstraintProvider.cle(p, jour), competence))
                .distinct();
        return InfirmiersConstraintProvider.competenceManquante(exigences);
    }
}
