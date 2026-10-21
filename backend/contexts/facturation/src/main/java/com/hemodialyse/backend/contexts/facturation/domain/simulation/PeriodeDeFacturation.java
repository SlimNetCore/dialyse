package com.hemodialyse.backend.contexts.facturation.domain.simulation;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

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
     * Tous les mois que l'intervalle touche, du premier au dernier (clôture mensuelle, RG-FAC-041).
     */
    public List<YearMonth> moisCouverts() {
        List<YearMonth> mois = new ArrayList<>();
        for (YearMonth courant = YearMonth.from(debut); !courant.isAfter(YearMonth.from(fin)); courant = courant.plusMonths(1)) {
            mois.add(courant);
        }
        return mois;
    }

    /**
     * Le mois s'il est donné, sinon les deux dates.
     */
    public static PeriodeDeFacturation de(YearMonth mois, LocalDate debut, LocalDate fin) {
        return mois != null ? deMois(mois) : new PeriodeDeFacturation(debut, fin);
    }
}
