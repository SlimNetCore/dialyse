package com.hemodialyse.backend.infrastructure.optimisation.timefold;

import ai.timefold.solver.core.api.domain.solution.ConstraintWeightOverrides;
import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import com.hemodialyse.backend.domain.planning.optimisation.model.ObjectifInfirmiers;
import com.hemodialyse.backend.domain.planning.optimisation.model.ParametresOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.PerimetreOptimisation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Poids des contraintes du solveur. Les contraintes sont écrites avec un poids unitaire ; l'arbitrage métier (ce qui
 * coûte plus cher que quoi) est centralisé ici et dérive des paramètres de l'optimisation.
 * <p>
 * Ordre de grandeur côté patients : une vacation d'infirmier économisée (100) vaut plus qu'une salle ouverte en moins
 * (30) ou qu'un générateur épargné (10) ; changer un patient de créneau coûte {@code stabilité × 18} (stabilité 5 : 90),
 * de salle {@code × 12}, de générateur seul {@code × 6}.
 */
public final class PoidsOptimisation {

    // Identifiants des contraintes : codes ASCII stables, traduits côté interface (planning.optimisation.contraintes.*)
    // — Patients —
    public static final String GENERATEUR_DOUBLE = "GENERATEUR_DOUBLE";
    public static final String ISOLEMENT = "ISOLEMENT";
    public static final String PATIENT_NON_PLACE = "PATIENT_NON_PLACE";
    public static final String INFIRMIERS_REQUIS = "INFIRMIERS_REQUIS";
    public static final String SALLES_OUVERTES = "SALLES_OUVERTES";
    public static final String GENERATEURS_UTILISES = "GENERATEURS_UTILISES";
    public static final String RESERVE_SECOURS = "RESERVE_SECOURS";
    public static final String STABILITE_PATIENTS = "STABILITE_PATIENTS";

    // — Infirmiers —
    public static final String VACATION_DOUBLE_CRENEAU = "VACATION_DOUBLE_CRENEAU";
    public static final String MAX_VACATIONS_JOUR = "MAX_VACATIONS_JOUR";
    public static final String VACATION_NON_POURVUE = "VACATION_NON_POURVUE";
    public static final String DEPASSEMENT_HEBDOMADAIRE = "DEPASSEMENT_HEBDOMADAIRE";
    public static final String EQUITE_CHARGE = "EQUITE_CHARGE";
    public static final String INFIRMIERS_MOBILISES = "INFIRMIERS_MOBILISES";
    public static final String DOUBLE_VACATION = "DOUBLE_VACATION";
    public static final String CONTINUITE_SALLE = "CONTINUITE_SALLE";
    public static final String AFFINITE = "AFFINITE";
    public static final String STABILITE_ROULEMENT = "STABILITE_ROULEMENT";
    public static final String QUALIFICATION = "QUALIFICATION";

    private static final int POIDS_INFIRMIERS_REQUIS = 100;
    private static final int POIDS_SALLES_OUVERTES = 30;
    private static final int POIDS_GENERATEURS = 10;
    private static final int POIDS_SECOURS = 50;
    private static final int POIDS_STABILITE_PATIENT = 6;
    private static final int POIDS_DEPASSEMENT = 500;
    private static final int POIDS_DOUBLE_VACATION = 40;
    private static final int POIDS_CONTINUITE = 30;
    private static final int POIDS_AFFINITE = 10;
    private static final int POIDS_STABILITE_ROULEMENT = 8;
    private static final int POIDS_QUALIFICATION = 200;
    private static final int POIDS_EQUITE = 10;
    private static final int POIDS_EQUITE_ECONOMIE = 1;
    private static final int POIDS_MOBILISES_ECONOMIE = 300;

    private PoidsOptimisation() {
    }

    public static ConstraintWeightOverrides<HardMediumSoftScore> patients(ParametresOptimisation p) {
        Map<String, HardMediumSoftScore> poids = new LinkedHashMap<>();
        poids.put(GENERATEUR_DOUBLE, HardMediumSoftScore.ofHard(1));
        poids.put(ISOLEMENT, HardMediumSoftScore.ofHard(1));
        poids.put(PATIENT_NON_PLACE, HardMediumSoftScore.ofMedium(1));
        poids.put(INFIRMIERS_REQUIS, HardMediumSoftScore.ofSoft(POIDS_INFIRMIERS_REQUIS));
        poids.put(SALLES_OUVERTES, HardMediumSoftScore.ofSoft(POIDS_SALLES_OUVERTES));
        poids.put(GENERATEURS_UTILISES, HardMediumSoftScore.ofSoft(POIDS_GENERATEURS));
        poids.put(RESERVE_SECOURS, HardMediumSoftScore.ofSoft(POIDS_SECOURS));
        poids.put(STABILITE_PATIENTS, HardMediumSoftScore.ofSoft(p.stabilite() * POIDS_STABILITE_PATIENT));
        return ConstraintWeightOverrides.of(poids);
    }

    public static ConstraintWeightOverrides<HardMediumSoftScore> infirmiers(ParametresOptimisation p) {
        boolean economie = p.objectif() == ObjectifInfirmiers.ECONOMIE;
        Map<String, HardMediumSoftScore> poids = new LinkedHashMap<>();
        poids.put(VACATION_DOUBLE_CRENEAU, HardMediumSoftScore.ofHard(1));
        poids.put(MAX_VACATIONS_JOUR, HardMediumSoftScore.ofHard(1));
        poids.put(VACATION_NON_POURVUE, HardMediumSoftScore.ofMedium(1));
        poids.put(DEPASSEMENT_HEBDOMADAIRE, HardMediumSoftScore.ofSoft(POIDS_DEPASSEMENT));
        poids.put(EQUITE_CHARGE, HardMediumSoftScore.ofSoft(economie ? POIDS_EQUITE_ECONOMIE : POIDS_EQUITE));
        poids.put(INFIRMIERS_MOBILISES, HardMediumSoftScore.ofSoft(economie ? POIDS_MOBILISES_ECONOMIE : 0));
        poids.put(DOUBLE_VACATION, HardMediumSoftScore.ofSoft(POIDS_DOUBLE_VACATION));
        poids.put(CONTINUITE_SALLE, HardMediumSoftScore.ofSoft(POIDS_CONTINUITE));
        poids.put(AFFINITE, HardMediumSoftScore.ofSoft(POIDS_AFFINITE));
        // en couverture, les vacations à pourvoir sont par nature hors du roulement : la stabilité n'a pas de sens
        int stabilite = p.perimetre() == PerimetreOptimisation.COUVERTURE ? 0 : p.stabilite();
        poids.put(STABILITE_ROULEMENT, HardMediumSoftScore.ofSoft(stabilite * POIDS_STABILITE_ROULEMENT));
        poids.put(QUALIFICATION, HardMediumSoftScore.ofSoft(POIDS_QUALIFICATION));
        return ConstraintWeightOverrides.of(poids);
    }
}
