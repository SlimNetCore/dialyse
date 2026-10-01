package com.hemodialyse.backend.domain.gmao.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Rectification d'une intervention terminée : motif, auteur, date, et clôture annulée (pour conserver la
 * trace de ce qui a été rouvert).
 */
public record RectificationIntervention(
        UUID id,
        String motif,
        UUID par,
        OffsetDateTime le,
        OffsetDateTime clotureAnterieureLe,
        UUID clotureAnterieurePar
) {
}
