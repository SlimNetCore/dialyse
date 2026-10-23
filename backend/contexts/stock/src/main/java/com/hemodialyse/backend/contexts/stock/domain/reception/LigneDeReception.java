package com.hemodialyse.backend.contexts.stock.domain.reception;

import com.hemodialyse.backend.contexts.stock.domain.commandes.BonDeCommandeInvalide;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Ligne d'un bon de réception : un article, une quantité strictement positive, un prix unitaire facultatif mais jamais
 * négatif, le lot reçu (numéro, péremption, emplacement) et, une fois le bon validé, l'identifiant du lot créé
 * ({@code lotId}, KB-STK-37 corrigé : le lien ligne-lot n'est plus perdu).
 */
public record LigneDeReception(UUID id, UUID articleId, BigDecimal quantite, BigDecimal prixUnitaire, String numeroLot,
                               LocalDate datePeremption, UUID emplacementId, UUID lotId) {

    /**
     * @throws BonDeCommandeInvalide article absent, quantité absente ou non strictement positive, prix négatif (400)
     */
    public static LigneDeReception saisie(UUID id, UUID articleId, BigDecimal quantite, BigDecimal prixUnitaire, String numeroLot,
                                          LocalDate datePeremption, UUID emplacementId) {
        if (articleId == null) {
            throw new BonDeCommandeInvalide("Article requis sur chaque ligne de réception");
        }
        if (quantite == null || quantite.signum() <= 0) {
            throw new BonDeCommandeInvalide("La quantité d'une ligne de réception doit être strictement positive");
        }
        if (prixUnitaire != null && prixUnitaire.signum() < 0) {
            throw new BonDeCommandeInvalide("Le prix unitaire d'une ligne de réception ne peut pas être négatif");
        }
        return new LigneDeReception(id, articleId, quantite, prixUnitaire, numeroLot, datePeremption, emplacementId, null);
    }

    public LigneDeReception avecLot(UUID lot) {
        return new LigneDeReception(id, articleId, quantite, prixUnitaire, numeroLot, datePeremption, emplacementId, lot);
    }
}

