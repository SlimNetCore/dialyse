package com.hemodialyse.backend.contexts.planification.interfaces.http.dto;

import com.hemodialyse.backend.contexts.planification.application.AbsenceLue;
import com.hemodialyse.backend.contexts.planification.domain.TypeAbsence;

import java.time.LocalDate;
import java.util.UUID;

public record AbsenceReponse(UUID id, UUID infirmierId, LocalDate debut, LocalDate fin, TypeAbsence type,
                             String motif) {
    public static AbsenceReponse depuis(AbsenceLue a) {
        return new AbsenceReponse(a.id(), a.infirmier(), a.debut(), a.fin(), a.type(), a.motif());
    }
}
