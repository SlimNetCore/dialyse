package com.hemodialyse.backend.contexts.gmao.application.piecejointe;

/**
 * Pièce inconnue, ou d'une autre intervention. Étend {@link IllegalArgumentException} pour reproduire le contrat observé :
 * HTTP 400 BAD_REQUEST « Document non trouvé » et non 404 (KB-GMAO-63).
 */
public class PieceJointeIntrouvable extends IllegalArgumentException {
    public PieceJointeIntrouvable() {
        super("Document non trouvé");
    }
}
