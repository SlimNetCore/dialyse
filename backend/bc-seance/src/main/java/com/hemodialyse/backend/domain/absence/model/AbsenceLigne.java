package com.hemodialyse.backend.domain.absence.model;

/**
 * Absence accompagnée du nom du patient, pour l'affichage des listes.
 */
public record AbsenceLigne(AbsencePatient absence, String patientNom) {
}
