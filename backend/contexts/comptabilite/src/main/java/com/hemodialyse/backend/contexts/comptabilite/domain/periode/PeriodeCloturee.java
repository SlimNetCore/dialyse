package com.hemodialyse.backend.contexts.comptabilite.domain.periode;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.RegleMetierViolee;

import java.time.YearMonth;

/**
 * Une écriture générée tomberait dans un mois clôturé ({@code PERIODE_CLOTUREE}, RG-CPT-005).
 */
public class PeriodeCloturee extends RegleMetierViolee {
    public PeriodeCloturee(CentreId centre, YearMonth mois) {
        super("PERIODE_CLOTUREE", "La période " + mois + " est clôturée pour le centre " + centre.valeur());
    }

    private PeriodeCloturee(String message) {
        super("PERIODE_CLOTUREE", message);
    }

    /**
     * Le refus tel que le formulent la saisie et l'extourne d'une pièce (C4, message historique sans le centre, reproduit).
     */
    public static PeriodeCloturee aLaSaisie(YearMonth mois) {
        return new PeriodeCloturee("La période " + mois + " est clôturée");
    }
}


