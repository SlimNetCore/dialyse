package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

/** Une pièce jointe sans ses octets (retrait) : toutes les colonnes sauf {@code contenu}. */
public record PieceEnTete(UUID id, UUID interventionId, UUID centreId, String type, String nom, String contentType, long taille,
                          UUID ajoutePar, Instant ajouteLe) {
}
