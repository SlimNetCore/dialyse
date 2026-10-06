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
 * de salle {@code × 12}, de générateur seul {@code × 6}. Abandonner un jour de dialyse habituel coûte
 * {@code stabilité × 20}, s'écarter du créneau préféré 40, séparer deux patients d'un même transporteur 15 par jour.
 * <p>
 * Côté personnel : un jour de repos manqué (300) et chaque heure au-delà du quota (100, soit 500 pour une vacation de
 * 5 h) pèsent autant qu'un dépassement hebdomadaire ; une compétence absente d'une case 250.
 * <p>
 * Modèle conjoint : une vacation tenue sans besoin (50) coûte moins que ce que rapporte une case fermée (vacation requise
 * et salle ouverte), sinon regrouper des patients serait bloqué tant que l'infirmier n'a pas été retiré.
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
    public static final String ESPACEMENT_JOURS = "ESPACEMENT_JOURS";
    public static final String CHANGEMENT_JOURS = "CHANGEMENT_JOURS";
    public static final String CRENEAU_PREFERE = "CRENEAU_PREFERE";
    public static final String TRANSPORT_PARTAGE = "TRANSPORT_PARTAGE";

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
    public static final String REPOS_HEBDOMADAIRE = "REPOS_HEBDOMADAIRE";
    public static final String QUOTA_HEURES = "QUOTA_HEURES";
    public static final String COMPETENCE = "COMPETENCE";

    // — Modèle conjoint (en plus des contraintes patients et infirmiers) —
    public static final String VACATION_INUTILE = "VACATION_INUTILE";

    // — Maintenance —
    public static final String POSTE_TEMPORAIRE_DOUBLE = "POSTE_TEMPORAIRE_DOUBLE";
    public static final String SEANCE_SANS_SOLUTION = "SEANCE_SANS_SOLUTION";
    public static final String CHANGEMENT_CRENEAU_TEMPORAIRE = "CHANGEMENT_CRENEAU_TEMPORAIRE";
    public static final String CHANGEMENT_SALLE_TEMPORAIRE = "CHANGEMENT_SALLE_TEMPORAIRE";

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
    private static final int POIDS_ESPACEMENT = 2;
    private static final int POIDS_CHANGEMENT_JOURS = 20;
    private static final int POIDS_CRENEAU_PREFERE = 40;
    private static final int POIDS_TRANSPORT = 15;
    private static final int POIDS_REPOS = 300;
    private static final int POIDS_QUOTA_HEURE = 100;
    private static final int POIDS_COMPETENCE = 250;
    private static final int POIDS_VACATION_INUTILE = 50;
    private static final int POIDS_CRENEAU_TEMPORAIRE = 100;
    private static final int POIDS_SALLE_TEMPORAIRE = 30;

    private PoidsOptimisation() {
    }

    public static ConstraintWeightOverrides<HardMediumSoftScore> patients(ParametresOptimisation p) {
        return ConstraintWeightOverrides.of(poidsPatients(p));
    }

    private static Map<String, HardMediumSoftScore> poidsPatients(ParametresOptimisation p) {
        Map<String, HardMediumSoftScore> poids = new LinkedHashMap<>();
        poids.put(GENERATEUR_DOUBLE, HardMediumSoftScore.ofHard(1));
        poids.put(ISOLEMENT, HardMediumSoftScore.ofHard(1));
        poids.put(PATIENT_NON_PLACE, HardMediumSoftScore.ofMedium(1));
        poids.put(INFIRMIERS_REQUIS, HardMediumSoftScore.ofSoft(POIDS_INFIRMIERS_REQUIS));
        poids.put(SALLES_OUVERTES, HardMediumSoftScore.ofSoft(POIDS_SALLES_OUVERTES));
        poids.put(GENERATEURS_UTILISES, HardMediumSoftScore.ofSoft(POIDS_GENERATEURS));
        poids.put(RESERVE_SECOURS, HardMediumSoftScore.ofSoft(POIDS_SECOURS));
        poids.put(STABILITE_PATIENTS, HardMediumSoftScore.ofSoft(p.stabilite() * POIDS_STABILITE_PATIENT));
        poids.put(ESPACEMENT_JOURS, HardMediumSoftScore.ofSoft(POIDS_ESPACEMENT));
        poids.put(CHANGEMENT_JOURS, HardMediumSoftScore.ofSoft(Math.max(1, p.stabilite()) * POIDS_CHANGEMENT_JOURS));
        poids.put(CRENEAU_PREFERE, HardMediumSoftScore.ofSoft(POIDS_CRENEAU_PREFERE));
        poids.put(TRANSPORT_PARTAGE, HardMediumSoftScore.ofSoft(POIDS_TRANSPORT));
        return poids;
    }

    public static ConstraintWeightOverrides<HardMediumSoftScore> infirmiers(ParametresOptimisation p) {
        return ConstraintWeightOverrides.of(poidsInfirmiers(p));
    }

    /**
     * Modèle conjoint : contraintes patients et infirmiers, plus les vacations tenues sans patient à suivre.
     */
    public static ConstraintWeightOverrides<HardMediumSoftScore> complet(ParametresOptimisation p) {
        Map<String, HardMediumSoftScore> poids = new LinkedHashMap<>(poidsPatients(p));
        poids.putAll(poidsInfirmiers(p));
        poids.put(VACATION_INUTILE, HardMediumSoftScore.ofSoft(POIDS_VACATION_INUTILE));
        return ConstraintWeightOverrides.of(poids);
    }

    /**
     * Déplacements temporaires : la place temporaire reste au plus près de l'habitude (même créneau, même salle).
     */
    public static ConstraintWeightOverrides<HardMediumSoftScore> maintenance() {
        Map<String, HardMediumSoftScore> poids = new LinkedHashMap<>();
        poids.put(POSTE_TEMPORAIRE_DOUBLE, HardMediumSoftScore.ofHard(1));
        poids.put(SEANCE_SANS_SOLUTION, HardMediumSoftScore.ofMedium(1));
        poids.put(CHANGEMENT_CRENEAU_TEMPORAIRE, HardMediumSoftScore.ofSoft(POIDS_CRENEAU_TEMPORAIRE));
        poids.put(CHANGEMENT_SALLE_TEMPORAIRE, HardMediumSoftScore.ofSoft(POIDS_SALLE_TEMPORAIRE));
        return ConstraintWeightOverrides.of(poids);
    }

    private static Map<String, HardMediumSoftScore> poidsInfirmiers(ParametresOptimisation p) {
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
        poids.put(REPOS_HEBDOMADAIRE, HardMediumSoftScore.ofSoft(POIDS_REPOS));
        poids.put(QUOTA_HEURES, HardMediumSoftScore.ofSoft(POIDS_QUOTA_HEURE));
        poids.put(COMPETENCE, HardMediumSoftScore.ofSoft(POIDS_COMPETENCE));
        return poids;
    }
}
