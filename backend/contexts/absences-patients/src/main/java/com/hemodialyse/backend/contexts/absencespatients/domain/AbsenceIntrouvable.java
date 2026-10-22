package com.hemodialyse.backend.contexts.absencespatients.domain;

import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.AbsenceId;

/**
 * Absence inconnue ou appartenant à un autre centre (indistinguables, RG-ABS-023) : 404 {@code ABSENCE_INTROUVABLE}
 * (était 422, KB-ABS-03 corrigé).
 */
public class AbsenceIntrouvable extends RuntimeException {
    private final AbsenceId absence;

    public AbsenceIntrouvable(AbsenceId absence) {
        super("Absence introuvable");
        this.absence = absence;
    }

    public AbsenceId absence() {
        return absence;
    }
}
