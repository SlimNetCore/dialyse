package com.hemodialyse.backend.contexts.comptabilite.contrat;

import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.NumerotationDesDocuments;

import java.util.UUID;

class NumerotationDesDocumentsEnMemoireContratTest extends NumerotationDesDocumentsContrat {

    private final NumerotationDesDocumentsEnMemoire double_ = new NumerotationDesDocumentsEnMemoire();

    @Override
    protected NumerotationDesDocuments numerotation() {
        return double_;
    }

    @Override
    protected void precharger(UUID centre, String type, String prefixe, long compteur) {
        double_.precharger(centre, type, prefixe, compteur);
    }
}
