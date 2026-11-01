package com.hemodialyse.backend.contexts.stock.interfaces.http.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Article favori du périmètre de sortie pour séances, avec les quantités sorties et restantes.
 */
public record FavoriReponse(UUID articleId, BigDecimal quantiteSortie, BigDecimal reste) {
}
