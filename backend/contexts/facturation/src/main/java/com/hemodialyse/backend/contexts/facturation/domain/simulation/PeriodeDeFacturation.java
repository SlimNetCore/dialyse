package com.hemodialyse.backend.contexts.facturation.domain.simulation;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Période d'une simulation de facturation (RG-FAC-020) : un mois entier ou deux dates, fin non antérieure au début. Le mois,
 * s'il est donné, l'emporte sur les deux dates.
 */
public record PeriodeDeFacturation(LocalDate debut, LocalDate fin) {

    public PeriodeDeFacturation {
        if (debut == null || fin == null) {
            throw new IllegalArgumentException("La periode de facturation est obligatoire");
        }
        if (fin.isBefore(debut)) {
            throw new IllegalArgumentException("La date de fin ne peut pas etre avant la date de debut");
        }
    }

    public static PeriodeDeFacturation deMois(YearMonth mois) {
        if (mois == null) {
            throw new IllegalArgumentException("Le mois est obligatoire");
        }
        return new PeriodeDeFacturation(mois.atDay(1), mois.atEndOfMonth());
    }

    /**
     * Le mois s'il est donné, sinon les deux dates.
     */
    public static PeriodeDeFacturation de(YearMonth mois, LocalDate debut, LocalDate fin) {
        return mois != null ? deMois(mois) : new PeriodeDeFacturation(debut, fin);
    }
}
