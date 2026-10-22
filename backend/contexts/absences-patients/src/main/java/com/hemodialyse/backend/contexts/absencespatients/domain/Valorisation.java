package com.hemodialyse.backend.contexts.absencespatients.domain;

import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.ForfaitId;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Valeur d'une absence, figée à sa création et jamais recalculée (RG-ABS-030 à 032) : forfait applicable (prix hors taxe),
 * taux de TVA du jour de la séance manquée et montant TTC qui en découle. {@code montantTtc} est le <em>montant</em> perdu,
 * pas un prix unitaire. Aucun montant ni taux négatif.
 */
public record Valorisation(ForfaitId forfait, String libelleDuForfait, BigDecimal montantHt, BigDecimal tauxDeTva,
                           BigDecimal montantTtc) {

    private static final int DECIMALES = 2;

    public Valorisation {
        if (montantHt == null || montantHt.signum() < 0) throw new IllegalArgumentException("Montant HT invalide");
        if (tauxDeTva == null || tauxDeTva.signum() < 0) throw new IllegalArgumentException("Taux de TVA invalide");
        if (montantTtc == null || montantTtc.signum() < 0) throw new IllegalArgumentException("Montant TTC invalide");
    }

    /**
     * HT = prix du forfait arrondi à deux décimales (demi vers le haut) ; TTC = HT x (1 + taux / 100) arrondi de même ; sans
     * forfait, la valeur est nulle. Le taux est rendu avec l'échelle de la base (au moins deux décimales : 0.00).
     */
    public static Valorisation depuis(ForfaitApplicable forfait, BigDecimal tauxDeTva) {
        BigDecimal prixHt = forfait == null ? BigDecimal.ZERO : forfait.prixHt();
        BigDecimal ht = prixHt.setScale(DECIMALES, RoundingMode.HALF_UP);
        BigDecimal ttc = ht.multiply(BigDecimal.ONE.add(tauxDeTva.movePointLeft(2))).setScale(DECIMALES, RoundingMode.HALF_UP);
        BigDecimal taux = tauxDeTva.scale() < DECIMALES ? tauxDeTva.setScale(DECIMALES) : tauxDeTva;
        return new Valorisation(forfait == null ? null : forfait.forfait(), forfait == null ? null : forfait.libelle(), ht,
                taux, ttc);
    }
}
