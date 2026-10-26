package com.hemodialyse.backend.contexts.planification.publishedlanguage;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Langage publié de lecture du roulement, des absences et des remplacements des infirmiers (ADR 0053, slice P3) : ce que la présence,
 * la charge, les alertes et l'optimisation lisent (slices P4 et P8, encore dans le legacy), sans connaître le modèle du contexte ni
 * ses tables. Lecture seule, bornée au centre demandé.
 */
public interface RoulementEtAbsencesDesInfirmiers {

    /**
     * Une affectation du roulement d'un infirmier ACTIF : salle, créneau et jours de la semaine.
     */
    record Affectation(UUID id, UUID infirmier, UUID salle, UUID creneau, Set<DayOfWeek> jours) {
    }

    /**
     * Une absence, bornes incluses ; {@code type} : {@code CONGE}, {@code MALADIE}, {@code FORMATION} ou {@code AUTRE}.
     */
    record Absence(UUID id, UUID infirmier, LocalDate debut, LocalDate fin, String type, String motif) {
    }

    /**
     * Un remplacement ponctuel ; {@code remplace} facultatif.
     */
    record Remplacement(UUID id, LocalDate date, UUID salle, UUID creneau, UUID infirmier, UUID remplace) {
    }

    /**
     * Le roulement des infirmiers actifs du centre.
     */
    List<Affectation> affectationsDesInfirmiersActifs(UUID centre);

    /**
     * Les absences qui partagent au moins un jour avec la période (bornes incluses).
     */
    List<Absence> absencesSurLaPeriode(UUID centre, LocalDate du, LocalDate au);

    /**
     * Les remplacements dont la date est dans la période (bornes incluses).
     */
    List<Remplacement> remplacementsSurLaPeriode(UUID centre, LocalDate du, LocalDate au);
}
