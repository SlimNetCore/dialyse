package com.hemodialyse.backend.domain.absence.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Valeur d'une absence figée à sa création : forfait de la prise en charge applicable à la date (prix HT, comme en
 * facturation), taux du type de TVA actif ce jour-là et montant TTC qui en découle
 * ({@code TTC = HT * (1 + taux / 100)}).
 */
public record ValeurAbsence(UUID forfaitId, String forfaitLibelle, BigDecimal prixTtc, BigDecimal tauxTva,
                            BigDecimal montantHt) {

    public ValeurAbsence {
        if (prixTtc == null || prixTtc.signum() < 0) throw new IllegalArgumentException("Prix TTC invalide");
        if (tauxTva == null || tauxTva.signum() < 0) throw new IllegalArgumentException("Taux de TVA invalide");
        if (montantHt == null || montantHt.signum() < 0) throw new IllegalArgumentException("Montant HT invalide");
    }

    /**
     * Le prix du forfait est hors taxe : le TTC en découle avec le taux de TVA (arrondi à deux décimales).
     */
    public static ValeurAbsence depuisHt(UUID forfaitId, String forfaitLibelle, BigDecimal prixHt, BigDecimal tauxTva) {
        BigDecimal ht = prixHt.setScale(2, RoundingMode.HALF_UP);
        BigDecimal ttc = ht.multiply(BigDecimal.ONE.add(tauxTva.movePointLeft(2))).setScale(2, RoundingMode.HALF_UP);
        return new ValeurAbsence(forfaitId, forfaitLibelle, ttc, tauxTva, ht);
    }
}
