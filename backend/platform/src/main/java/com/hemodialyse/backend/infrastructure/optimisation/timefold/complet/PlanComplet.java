package com.hemodialyse.backend.infrastructure.optimisation.timefold.complet;

import ai.timefold.solver.core.api.domain.solution.ConstraintWeightOverrides;
import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.infirmiers.InfirmierPlan;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.infirmiers.Vacation;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.patients.ContexteCentre;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.patients.PlacementPatient;

import java.util.List;

/**
 * Problème conjoint d'un centre : placement des patients et roulement des infirmiers résolus ensemble. Chaque case
 * (salle, créneau, jour) dispose de vacations potentielles ; le besoin d'une case suit, pendant le calcul, le nombre de
 * patients qui y sont placés, et un placement est d'autant plus intéressant que des infirmiers sont disponibles pour le
 * servir.
 */
@PlanningSolution
public class PlanComplet {

    @ProblemFactCollectionProperty
    private List<ContexteCentre> contexte;

    @ProblemFactCollectionProperty
    private List<InfirmierPlan> infirmiers;

    @PlanningEntityCollectionProperty
    private List<PlacementPatient> patients;

    @PlanningEntityCollectionProperty
    private List<Vacation> vacations;

    private ConstraintWeightOverrides<HardMediumSoftScore> poids;

    @PlanningScore
    private HardMediumSoftScore score;

    public PlanComplet() {
    }

    public PlanComplet(ContexteCentre contexte, List<InfirmierPlan> infirmiers, List<PlacementPatient> patients,
                       List<Vacation> vacations, ConstraintWeightOverrides<HardMediumSoftScore> poids) {
        this.contexte = List.of(contexte);
        this.infirmiers = infirmiers;
        this.patients = patients;
        this.vacations = vacations;
        this.poids = poids;
    }

    public List<ContexteCentre> getContexte() {
        return contexte;
    }

    public void setContexte(List<ContexteCentre> contexte) {
        this.contexte = contexte;
    }

    public List<InfirmierPlan> getInfirmiers() {
        return infirmiers;
    }

    public void setInfirmiers(List<InfirmierPlan> infirmiers) {
        this.infirmiers = infirmiers;
    }

    public List<PlacementPatient> getPatients() {
        return patients;
    }

    public void setPatients(List<PlacementPatient> patients) {
        this.patients = patients;
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
