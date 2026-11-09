package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Chiffre d'affaires HT et TTC et encaissé d'un ensemble de factures. Le reste à recouvrer est la différence TTC - encaissé, jamais
 * négative ; le taux d'encaissement est l'encaissé en pourcentage du TTC, à une décimale (HALF_UP), absent quand le TTC est nul.
 */
public record ChiffresFinanciers(BigDecimal caHt, BigDecimal caTtc, BigDecimal encaisse) {

    public static ChiffresFinanciers zero() {
        return new ChiffresFinanciers(montantNul(), montantNul(), montantNul());
    }

    /** Zéro à deux décimales, forme servie dans le JSON. */
    public static BigDecimal montantNul() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal resteARecouvrer() {
        return caTtc.subtract(encaisse).max(BigDecimal.ZERO);
    }

    public BigDecimal tauxEncaissement() {
        return caTtc.signum() == 0 ? null : encaisse.multiply(BigDecimal.valueOf(100)).divide(caTtc, 1, RoundingMode.HALF_UP);
    }
}
