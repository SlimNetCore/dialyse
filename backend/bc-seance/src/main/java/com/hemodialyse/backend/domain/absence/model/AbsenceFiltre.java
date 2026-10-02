package com.hemodialyse.backend.domain.absence.model;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Critères de recherche du suivi des absences ; tous facultatifs.
 */
public record AbsenceFiltre(StatutAbsence statut, MotifAbsence motif, UUID patientId, LocalDate from, LocalDate to) {
    public static AbsenceFiltre aucun() {
        return new AbsenceFiltre(null, null, null, null, null);
    }
}
