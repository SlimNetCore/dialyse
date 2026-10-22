package com.hemodialyse.backend.contexts.absencespatients.domain;

import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;

import java.time.LocalDate;

/**
 * Critères de recherche du suivi des absences, tous facultatifs ; {@code du} et {@code au} sont inclusifs. Des bornes
 * incohérentes ({@code du} après {@code au}) ne sont pas une erreur : elles ne retiennent rien.
 */
public record FiltreDesAbsences(StatutAbsence statut, MotifAbsence motif, PatientId patient, LocalDate du, LocalDate au) {

    public static FiltreDesAbsences aucun() {
        return new FiltreDesAbsences(null, null, null, null, null);
    }
}
