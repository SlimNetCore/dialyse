package com.hemodialyse.backend.infrastructure.optimisation.timefold.infirmiers;

import ai.timefold.solver.core.api.domain.solution.ConstraintWeightOverrides;
import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.score.HardMediumSoftScore;

import java.util.List;

/**
 * Problème de planification des infirmiers : qui tient chaque vacation exigée par le ratio de sécurité, en respectant
 * absences, habilitations et limites de charge, avec une charge équitable (ou la plus économe en personnel).
 */
@PlanningSolution
public class PlanInfirmiers {

    @ProblemFactCollectionProperty
    private List<InfirmierPlan> infirmiers;

    @PlanningEntityCollectionProperty
    private List<Vacation> vacations;

    private ConstraintWeightOverrides<HardMediumSoftScore> poids;

    @PlanningScore
    private HardMediumSoftScore score;

    public PlanInfirmiers() {
    }

    public PlanInfirmiers(List<InfirmierPlan> infirmiers, List<Vacation> vacations,
                          ConstraintWeightOverrides<HardMediumSoftScore> poids) {
        this.infirmiers = infirmiers;
        this.vacations = vacations;
        this.poids = poids;
    }

    public List<InfirmierPlan> getInfirmiers() {
        return infirmiers;
    }

    public void setInfirmiers(List<InfirmierPlan> infirmiers) {
        this.infirmiers = infirmiers;
    }

    public List<Vacation> getVacations() {
        return vacations;
    }

    public void setVacations(List<Vacation> vacations) {
        this.vacations = vacations;
    }

    public ConstraintWeightOverrides<HardMediumSoftScore> getPoids() {
        return poids;
    }

    public void setPoids(ConstraintWeightOverrides<HardMediumSoftScore> poids) {
        this.poids = poids;
    }

    public HardMediumSoftScore getScore() {
        return score;
    }

    public void setScore(HardMediumSoftScore score) {
        this.score = score;
    }
}
