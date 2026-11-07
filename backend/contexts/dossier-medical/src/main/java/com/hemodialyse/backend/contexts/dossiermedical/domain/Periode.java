package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.time.LocalDate;

/** Période de recherche des prescriptions, bornes incluses et facultatives. Début après fin : 400 {@code BAD_REQUEST} (reproduit). */
public record Periode(LocalDate du, LocalDate au) {

    public static final Periode ILLIMITEE = new Periode(null, null);

    public Periode {
        if (du != null && au != null && du.isAfter(au)) throw new SaisieInvalide("La date from doit être <= à la date to");
    }

    /** Jusqu'à une date incluse (prescription en vigueur). */
    public static Periode jusquau(LocalDate jour) {
        return new Periode(null, jour);
    }
}
