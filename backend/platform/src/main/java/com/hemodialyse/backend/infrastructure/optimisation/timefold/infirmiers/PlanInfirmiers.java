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
 * absences, habilitations, limites de charge, repos, quotas d'heures et compétences demandées par les patients, avec une
 * charge équitable (ou la plus économe en personnel).
 */
@PlanningSolution
public class PlanInfirmiers {

    @ProblemFactCollectionProperty
    private List<InfirmierPlan> infirmiers;

    @ProblemFactCollectionProperty
    private List<ExigenceCompetence> exigences;

    @PlanningEntityCollectionProperty
    private List<Vacation> vacations;

    private ConstraintWeightOverrides<HardMediumSoftScore> poids;

    @PlanningScore
    private HardMediumSoftScore score;

    public PlanInfirmiers() {
    }

    public PlanInfirmiers(List<InfirmierPlan> infirmiers, List<Vacation> vacations,
                          ConstraintWeightOverrides<HardMediumSoftScore> poids) {
        this(infirmiers, List.of(), vacations, poids);
    }

    public PlanInfirmiers(List<InfirmierPlan> infirmiers, List<ExigenceCompetence> exigences, List<Vacation> vacations,
                          ConstraintWeightOverrides<HardMediumSoftScore> poids) {
        this.infirmiers = infirmiers;
        this.exigences = exigences;
        this.vacations = vacations;
        this.poids = poids;
    }

    public List<ExigenceCompetence> getExigences() {
        return exigences;
    }

    public void setExigences(List<ExigenceCompetence> exigences) {
        this.exigences = exigences;
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
