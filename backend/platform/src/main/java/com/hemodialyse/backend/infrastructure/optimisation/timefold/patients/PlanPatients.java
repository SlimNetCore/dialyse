package com.hemodialyse.backend.infrastructure.optimisation.timefold.patients;

import ai.timefold.solver.core.api.domain.solution.ConstraintWeightOverrides;
import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.score.HardMediumSoftScore;

import java.util.List;

/**
 * Problème de placement des patients d'un centre : où dialyse chaque patient (générateur, créneau) pour consommer le
 * moins de ressources possible, sans jamais enfreindre les règles de la planification.
 */
@PlanningSolution
public class PlanPatients {

    @ProblemFactCollectionProperty
    private List<ContexteCentre> contexte;

    @PlanningEntityCollectionProperty
    private List<PlacementPatient> patients;

    private ConstraintWeightOverrides<HardMediumSoftScore> poids;

    @PlanningScore
    private HardMediumSoftScore score;

    public PlanPatients() {
    }

    public PlanPatients(ContexteCentre contexte, List<PlacementPatient> patients,
                        ConstraintWeightOverrides<HardMediumSoftScore> poids) {
        this.contexte = List.of(contexte);
        this.patients = patients;
        this.poids = poids;
    }

    public List<ContexteCentre> getContexte() {
        return contexte;
    }

    public void setContexte(List<ContexteCentre> contexte) {
        this.contexte = contexte;
    }

    public List<PlacementPatient> getPatients() {
        return patients;
    }

    public void setPatients(List<PlacementPatient> patients) {
        this.patients = patients;
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
