package com.hemodialyse.backend.infrastructure.web.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Réponse commune aux écritures du dossier médical : de quoi rafraîchir la liste côté client
 * sans renvoyer l'agrégat entier.
 */
public record EntityWriteResponse(
        UUID id,
        UUID patientId,
        OffsetDateTime updatedAt
) {
}
