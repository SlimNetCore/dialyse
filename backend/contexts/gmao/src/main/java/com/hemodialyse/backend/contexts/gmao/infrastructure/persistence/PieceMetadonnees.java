package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

/** Ligne de liste d'une pièce jointe : tout sauf les octets. */
public record PieceMetadonnees(UUID id, UUID interventionId, String type, String nom, String contentType, long taille,
                               Instant ajouteLe) {
}
