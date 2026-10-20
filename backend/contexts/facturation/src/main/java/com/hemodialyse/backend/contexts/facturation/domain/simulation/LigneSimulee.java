package com.hemodialyse.backend.contexts.facturation.domain.simulation;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Ligne d'une facture simulée : un forfait, son prix unitaire hors taxe et le nombre de séances qu'il couvre.
 */
public record LigneSimulee(ForfaitApplique forfait, int nombreDeSeances) {

    public LigneSimulee {
        if (forfait == null || nombreDeSeances <= 0) {
            throw new IllegalArgumentException("Ligne de simulation invalide");
        }
    }

    public BigDecimal prixUnitaireHt() {
        return forfait.prixHt();
    }

    public BigDecimal montantHt() {
        return forfait.prixHt().multiply(BigDecimal.valueOf(nombreDeSeances)).setScale(2, RoundingMode.HALF_UP);
    }
}
