package com.hemodialyse.backend.contexts.stock.domain.commandes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Ligne d'un bon de commande : un article, une quantité strictement positive et un prix unitaire facultatif (bon envoyé avant
 * chiffrage) mais jamais négatif (RG-STK-010 à 012).
 */
public record LigneDeBon(UUID id, UUID articleId, BigDecimal quantite, BigDecimal prixUnitaire) {

    /**
     * @throws BonDeCommandeInvalide article absent, quantité absente ou non strictement positive, prix négatif
     */
    public static LigneDeBon saisie(UUID id, UUID articleId, BigDecimal quantite, BigDecimal prixUnitaire) {
        if (articleId == null) {
            throw new BonDeCommandeInvalide("Article requis sur chaque ligne");
        }
        if (quantite == null || quantite.signum() <= 0) {
            throw new BonDeCommandeInvalide("La quantité d'une ligne doit être strictement positive");
        }
        if (prixUnitaire != null && prixUnitaire.signum() < 0) {
            throw new BonDeCommandeInvalide("Le prix unitaire d'une ligne ne peut pas être négatif");
        }
        return new LigneDeBon(id, articleId, quantite, prixUnitaire);
    }

    /**
     * Quantité x prix, arrondi à deux décimales ; zéro sans prix.
     */
    public BigDecimal sousTotal() {
        return prixUnitaire == null ? BigDecimal.ZERO : prixUnitaire.multiply(quantite).setScale(2, RoundingMode.HALF_UP);
    }
}

