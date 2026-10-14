package com.hemodialyse.backend.contexts.gmao.domain.piecejointe;

import java.util.UUID;

/** Identité d'une pièce jointe. */
public record PieceJointeId(UUID valeur) {
    public PieceJointeId {
        if (valeur == null) throw new IllegalArgumentException("Pièce jointe requise");
    }
}
