package com.hemodialyse.backend.contexts.stock.domain.sortie;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Ligne d'un bon de sortie : quantité sortie d'un lot d'un article, valorisée au PMP courant de l'article à la saisie
 * (RG-STK-030). Valeur immuable.
 */
public record LigneDeSortie(UUID id, UUID articleId, UUID lotId, BigDecimal quantite, BigDecimal pmpApplique) {
}

