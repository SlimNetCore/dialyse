package com.hemodialyse.backend.contexts.planification.interfaces.http.dto;

import com.hemodialyse.backend.contexts.planification.domain.PeriodeDAbsence;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Absence saisie : identifiants, dates et type lus tels quels, jugés par le domaine (400 si inutilisables).
 */
public record AbsenceDemande(UUID infirmierId, LocalDate debut, LocalDate fin, String type, String motif) {
    public PeriodeDAbsence periode() {
        return new PeriodeDAbsence(debut, fin);
    }
}
