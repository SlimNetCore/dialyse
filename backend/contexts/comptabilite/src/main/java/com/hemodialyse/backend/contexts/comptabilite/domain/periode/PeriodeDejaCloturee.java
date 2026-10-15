package com.hemodialyse.backend.contexts.comptabilite.domain.periode;

import com.hemodialyse.backend.contexts.comptabilite.domain.RegleMetierViolee;

import java.time.YearMonth;

/**
 * La période est déjà clôturée : une clôture ne se refait ni ne se défait ({@code PERIODE_DEJA_CLOTUREE}, 422, RG-CPT-005).
 */
public class PeriodeDejaCloturee extends RegleMetierViolee {
    PeriodeDejaCloturee(YearMonth mois) {
        super("PERIODE_DEJA_CLOTUREE", "La période " + mois + " est déjà clôturée");
    }
}
