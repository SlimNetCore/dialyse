package com.hemodialyse.backend.contexts.facturation.application.consultation;

import java.math.BigDecimal;

/** Totaux de TOUTES les factures filtrées (pas seulement de la page), à 2 décimales (RG-FAC-046). */
public record TotauxDesFactures(long nombreDeFactures, BigDecimal montantHt, BigDecimal montantTva, BigDecimal montantTtc,
                                BigDecimal montantRegle, BigDecimal reste) {
}
