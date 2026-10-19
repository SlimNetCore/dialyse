package com.hemodialyse.backend.contexts.comptabilite.domain.ecriture;

import com.hemodialyse.backend.contexts.comptabilite.domain.RegleMetierViolee;

/**
 * Une pièce ne s'extourne qu'une fois, et une extourne ne s'extourne pas ({@code PIECE_DEJA_EXTOURNEE}, RG-CPT-022).
 */
public class PieceDejaExtournee extends RegleMetierViolee {

    private PieceDejaExtournee(String message) {
        super("PIECE_DEJA_EXTOURNEE", message);
    }

    /**
     * L'écriture à extourner est elle-même une extourne.
     */
    static PieceDejaExtournee carCestUneExtourne() {
        return new PieceDejaExtournee("Une extourne ne s'extourne pas : saisissez une nouvelle pièce");
    }

    /**
     * L'extourne de la pièce existe déjà (constaté par le cas d'usage, qui seul lit les écritures enregistrées).
     */
    public static PieceDejaExtournee dejaFaite(NumeroDePiece piece) {
        return new PieceDejaExtournee("La pièce " + piece + " est déjà extournée");
    }
}

