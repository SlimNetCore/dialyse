package com.hemodialyse.backend.contexts.gmao.domain.piecejointe;

/** Le type de pièce demandé n'existe pas. HTTP 400 BAD_REQUEST, comme le legacy (KB-GMAO-63). */
public class TypeDePieceInconnu extends IllegalArgumentException {

    private final String code;

    public TypeDePieceInconnu(String code) {
        super("Type de pièce inconnu : " + code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
