package com.hemodialyse.backend.infrastructure.optimisation.timefold.maintenance;

import ai.timefold.solver.core.api.domain.solution.ConstraintWeightOverrides;
import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.score.HardMediumSoftScore;

import java.util.List;

/**
 * Problème des déplacements temporaires : replacer, date par date, les séances dont le générateur est en maintenance,
 * sur des places libres ce jour-là et au plus près des habitudes du patient.
 */
@PlanningSolution
public class PlanMaintenance {

    @PlanningEntityCollectionProperty
    private List<SeanceTemporaire> seances;

    private ConstraintWeightOverrides<HardMediumSoftScore> poids;

    @PlanningScore
    private HardMediumSoftScore score;

    public PlanMaintenance() {
    }

    public PlanMaintenance(List<SeanceTemporaire> seances, ConstraintWeightOverrides<HardMediumSoftScore> poids) {
        this.seances = seances;
        this.poids = poids;
    }

    public List<SeanceTemporaire> getSeances() {
        return seances;
    }

    public void setSeances(List<SeanceTemporaire> seances) {
        this.seances = seances;
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
