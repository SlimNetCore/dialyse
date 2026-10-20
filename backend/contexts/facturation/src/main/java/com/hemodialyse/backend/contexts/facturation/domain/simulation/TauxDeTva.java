package com.hemodialyse.backend.contexts.facturation.domain.simulation;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Taux de TVA en pourcentage (9 pour 9 %). La TVA d'un montant est arrondie à deux décimales (RG-FAC-022). Sans règle de TVA
 * applicable, le taux est 0 (RG-FAC-002).
 */
public record TauxDeTva(BigDecimal pourcentage) {

    public static final TauxDeTva AUCUN = new TauxDeTva(BigDecimal.ZERO);

    public TauxDeTva {
        if (pourcentage == null || pourcentage.signum() < 0) {
            throw new IllegalArgumentException("Taux de TVA invalide");
        }
    }

    public BigDecimal tvaSur(BigDecimal montantHt) {
        BigDecimal rapport = pourcentage.movePointLeft(2).setScale(6, RoundingMode.HALF_UP);
        return montantHt.multiply(rapport).setScale(2, RoundingMode.HALF_UP);
    }
}
