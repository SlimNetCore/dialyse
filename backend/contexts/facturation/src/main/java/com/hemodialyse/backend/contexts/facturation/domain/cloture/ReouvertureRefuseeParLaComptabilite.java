package com.hemodialyse.backend.contexts.facturation.domain.cloture;

import java.time.YearMonth;

/** Refus 422 {@code PERIODE_COMPTABLE_CLOTUREE} : la période comptable du même mois est clôturée, la facturation ne se rouvre pas. */
public final class ReouvertureRefuseeParLaComptabilite extends RuntimeException {
    public ReouvertureRefuseeParLaComptabilite(YearMonth mois) {
        super("La periode comptable " + mois + " est cloturee : la periode de facturation ne peut pas etre rouverte");
    }
}
