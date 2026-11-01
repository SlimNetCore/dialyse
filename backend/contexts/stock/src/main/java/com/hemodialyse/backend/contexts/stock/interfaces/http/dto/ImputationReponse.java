package com.hemodialyse.backend.contexts.stock.interfaces.http.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Imputation d'une consommation de séance sur un lot, avec son éventuelle alerte.
 */
public record ImputationReponse(UUID id, UUID articleId, UUID lotId, BigDecimal quantite, String alerte) {
}
