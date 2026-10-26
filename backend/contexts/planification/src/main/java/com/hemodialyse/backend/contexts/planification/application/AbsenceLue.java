package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.AbsenceInfirmier;
import com.hemodialyse.backend.contexts.planification.domain.TypeAbsence;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Une absence telle qu'affichée (read model : jamais l'agrégat vers l'interface).
 */
public record AbsenceLue(UUID id, UUID infirmier, LocalDate debut, LocalDate fin, TypeAbsence type, String motif) {

    static AbsenceLue depuis(AbsenceInfirmier a) {
        return new AbsenceLue(a.id().valeur(), a.infirmier().valeur(), a.periode().debut(), a.periode().fin(), a.type(),
                a.motif().orElse(null));
    }
}
