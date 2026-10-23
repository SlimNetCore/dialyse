package com.hemodialyse.backend.contexts.stock.domain.sortieseances;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Ligne d'une sortie pour séances : un lot retenu à l'enregistrement (quantité sortie, valeur unitaire de sortie), puis, au
 * rapprochement, la quantité consommée, l'écart (consommé − sorti) et son motif. Une ligne sans lot porte l'excédent d'un article
 * consommé au-delà de la pièce (quantité sortie 0, écart « en plus »).
 */
public record LigneDePiece(UUID id, UUID articleId, UUID lotId, BigDecimal quantiteSortie, BigDecimal valeurUnitaire,
                           BigDecimal quantiteConsommee, BigDecimal ecart, String motif) {
    public static LigneDePiece sortie(UUID id, UUID articleId, UUID lotId, BigDecimal quantite, BigDecimal valeurUnitaire) {
        return new LigneDePiece(id, articleId, lotId, quantite, valeurUnitaire, null, null, null);
    }

    public boolean aUnEcart() {
        return ecart != null && ecart.signum() != 0;
    }

    public LigneDePiece avecMotif(String nouveauMotif) {
        return new LigneDePiece(id, articleId, lotId, quantiteSortie, valeurUnitaire, quantiteConsommee, ecart, nouveauMotif);
    }
}