package com.hemodialyse.backend.contexts.gmao.domain.piecejointe;

/** R23 : l'intervention porte déjà le nombre maximal de pièces. HTTP 400 BAD_REQUEST, message du legacy. */
public class QuotaDePiecesAtteint extends IllegalArgumentException {
    public QuotaDePiecesAtteint() {
        super("Nombre maximal de documents atteint pour cette intervention");
    }
}
