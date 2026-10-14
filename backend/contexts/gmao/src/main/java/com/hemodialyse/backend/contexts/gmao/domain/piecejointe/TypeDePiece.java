package com.hemodialyse.backend.contexts.gmao.domain.piecejointe;

/** Nature d'une pièce jointe : à quoi elle sert (le format du fichier est un autre sujet, voir {@link FormatReconnu}). */
public enum TypeDePiece {
    BON_INTERVENTION,
    FACTURE,
    PHOTO,
    AUTRE;

    /** Sensible à la casse, comme le contrat observé (KB-GMAO-63) ; toute autre valeur : {@link TypeDePieceInconnu}. */
    public static TypeDePiece depuis(String code) {
        for (TypeDePiece type : values()) {
            if (type.name().equals(code)) return type;
        }
        throw new TypeDePieceInconnu(code);
    }
}
