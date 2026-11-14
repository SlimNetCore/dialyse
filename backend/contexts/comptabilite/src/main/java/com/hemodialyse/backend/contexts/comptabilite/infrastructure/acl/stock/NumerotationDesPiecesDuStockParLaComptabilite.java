package com.hemodialyse.backend.contexts.comptabilite.infrastructure.acl.stock;

import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.NumerotationDesDocuments;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.NumerotationDesPiecesFournie;

import java.util.UUID;

/**
 * Fournit au Stock le port {@code NumerotationDesPiecesFournie} (S-DDL2, ADR 0185) : traducteur mince vers la numérotation des
 * documents de la Comptabilité, qui possède les compteurs. La direction est celle de la dépendance Maven (Comptabilité → Stock).
 */
public final class NumerotationDesPiecesDuStockParLaComptabilite implements NumerotationDesPiecesFournie {

    private final NumerotationDesDocuments numerotation;

    public NumerotationDesPiecesDuStockParLaComptabilite(NumerotationDesDocuments numerotation) {
        this.numerotation = numerotation;
    }

    @Override
    public String suivante(UUID centre, String cle, String prefixeParDefaut) {
        return numerotation.suivant(centre, cle, prefixeParDefaut);
    }
}
