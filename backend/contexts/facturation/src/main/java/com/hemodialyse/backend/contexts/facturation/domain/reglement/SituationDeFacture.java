package com.hemodialyse.backend.contexts.facturation.domain.reglement;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Situation d'une facture (F13, RG-REG-002) : lecture calculée du total TTC figé contre la somme signée des règlements, au
 * centime. Rien de réglé (somme nulle, y compris après remboursement) : non réglée, reste = total ; somme inférieure au total :
 * partiellement réglée ; égale : réglée ; supérieure : réglée avec un trop-perçu égal à l'excédent.
 */
public record SituationDeFacture(EtatDeReglement etat, TypeDeSolde solde, BigDecimal reste, BigDecimal tropPercu) {

    public static SituationDeFacture evaluer(BigDecimal totalTtc, BigDecimal totalRegle) {
        BigDecimal total = arrondi(totalTtc);
        BigDecimal regle = arrondi(totalRegle);
        BigDecimal ecart = total.subtract(regle).setScale(2, RoundingMode.HALF_UP);
        if (regle.signum() == 0) {
            return new SituationDeFacture(EtatDeReglement.NON_REGLEE, TypeDeSolde.RESTE, total, BigDecimal.ZERO);
        }
        if (ecart.signum() > 0) {
            return new SituationDeFacture(EtatDeReglement.PARTIELLEMENT_REGLEE, TypeDeSolde.RESTE, ecart, BigDecimal.ZERO);
        }
        if (ecart.signum() < 0) {
            return new SituationDeFacture(EtatDeReglement.REGLEE, TypeDeSolde.TROP_PERCU, BigDecimal.ZERO, ecart.abs());
        }
        return new SituationDeFacture(EtatDeReglement.REGLEE, TypeDeSolde.REGLE, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private static BigDecimal arrondi(BigDecimal montant) {
        return montant == null ? BigDecimal.ZERO : montant.setScale(2, RoundingMode.HALF_UP);
    }
}
