package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.time.LocalDate;

/** Periode des statistiques d'un patient, bornes facultatives et incluses (nulle = sans borne). Debut apres la fin : {@link PeriodeInversee}. */
public record PeriodeDeStatistiques(LocalDate du, LocalDate au) {

    public PeriodeDeStatistiques {
        if (du != null && au != null && du.isAfter(au)) throw new PeriodeInversee();
    }
}
