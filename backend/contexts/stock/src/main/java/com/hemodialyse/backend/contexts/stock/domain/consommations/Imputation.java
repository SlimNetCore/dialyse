package com.hemodialyse.backend.contexts.stock.domain.consommations;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Quantité d'un article imputée sur un lot de la sortie pour séances ; sans lot, c'est un dépassement accepté avec alerte.
 *
 * @param lotId  lot de la pièce, ou {@code null} pour une quantité au-delà de la pièce
 * @param alerte {@code null}, {@link ConsommationDeSeance#DEPASSEMENT}, {@link ConsommationDeSeance#HORS_PIECE} ou
 *               {@link ConsommationDeSeance#AUCUNE_PIECE}
 */
public record Imputation(UUID id, UUID articleId, UUID lotId, BigDecimal quantite, String alerte) {
}