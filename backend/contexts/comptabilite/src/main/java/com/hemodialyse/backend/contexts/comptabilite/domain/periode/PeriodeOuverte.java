package com.hemodialyse.backend.contexts.comptabilite.domain.periode;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Preuve qu'un mois d'un centre est ouvert aux écritures (RG-CPT-005, R7). Seule {@link PeriodeComptable#exigerOuverte()}
 * la délivre : les fabriques de l'écriture comptable l'exigent, si bien qu'aucune écriture ne peut naître dans un mois
 * clôturé. Le cas d'usage l'obtient au moment où il contrôlait la période, ce qui garde l'ordre des refus.
 */
public final class PeriodeOuverte {

    private final CentreId centre;
    private final YearMonth mois;

    PeriodeOuverte(CentreId centre, YearMonth mois) {
        this.centre = centre;
        this.mois = mois;
    }

    public CentreId centre() {
        return centre;
    }

    public YearMonth mois() {
        return mois;
    }

    /**
     * Vrai si la date tombe dans ce mois, pour ce centre.
     */
    public boolean couvre(CentreId centre, LocalDate date) {
        return this.centre.equals(centre) && mois.equals(YearMonth.from(date));
    }
}

