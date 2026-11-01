package com.hemodialyse.backend.contexts.planification.interfaces.http.dto;

import com.hemodialyse.backend.contexts.planification.domain.PeriodeDAbsence;

import java.time.LocalDate;

public record MonAbsenceDemande(LocalDate debut, LocalDate fin, String type, String motif) {
    public PeriodeDAbsence periode() {
        return new PeriodeDAbsence(debut, fin);
    }
}
