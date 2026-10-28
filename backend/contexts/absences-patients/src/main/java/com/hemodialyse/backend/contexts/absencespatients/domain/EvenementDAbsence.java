package com.hemodialyse.backend.contexts.absencespatients.domain;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.UtilisateurId;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Événements de domaine d'une absence (internes au contexte, jamais publiés : aucun consommateur aujourd'hui). Ils servent
 * aux tests du domaine et, plus tard, au pilotage s'il passe aux événements.
 */
public sealed interface EvenementDAbsence {

    AbsenceId absence();

    CentreId centre();

    PatientId patient();

    LocalDate dateDeSeance();

    /** Nul quand l'absence est écrite sans auteur (session sans identité). */
    UtilisateurId auteur();

    Instant le();

    record AbsenceDetectee(AbsenceId absence, CentreId centre, PatientId patient, LocalDate dateDeSeance, UtilisateurId auteur,
                           Instant le) implements EvenementDAbsence {
    }

    record AbsenceReconciliee(AbsenceId absence, CentreId centre, PatientId patient, LocalDate dateDeSeance,
                              UtilisateurId auteur, Instant le) implements EvenementDAbsence {
    }

    record AbsenceDeclaree(AbsenceId absence, CentreId centre, PatientId patient, LocalDate dateDeSeance, UtilisateurId auteur,
                           Instant le) implements EvenementDAbsence {
    }

    record AbsenceQualifiee(AbsenceId absence, CentreId centre, PatientId patient, LocalDate dateDeSeance, UtilisateurId auteur,
                            Instant le, MotifAbsence motif) implements EvenementDAbsence {
    }

    record AbsenceRattrapee(AbsenceId absence, CentreId centre, PatientId patient, LocalDate dateDeSeance, UtilisateurId auteur,
                            Instant le, LocalDate dateDeRattrapage) implements EvenementDAbsence {
    }

    record AbsenceAnnulee(AbsenceId absence, CentreId centre, PatientId patient, LocalDate dateDeSeance, UtilisateurId auteur,
                          Instant le) implements EvenementDAbsence {
    }
}
