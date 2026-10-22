package com.hemodialyse.backend.contexts.absencespatients.domain;

/**
 * L'absence est rattrapée ou annulée : elle n'est plus modifiable (RG-ABS-003), donc aucune séance de rattrapage ne lui
 * est proposée (KB-ABS-04 corrigé). Code {@code ABSENCE_NON_MODIFIABLE}, comme pour les écritures.
 */
public class AbsenceNonModifiable extends RuntimeException {
    public AbsenceNonModifiable() {
        super("Cette absence n'est plus modifiable");
    }
}
