package com.hemodialyse.backend.contexts.stock.infrastructure.acl.documents;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.DeclarationDesTypesDeDocument;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.TypeDeDocumentDeclare;

import java.util.List;

/** Le type de document que le Stock possède (documents.v1, DOC4c, ADR 0166) : le procès-verbal d'inventaire. */
public final class TypesDeDocumentDuStock implements DeclarationDesTypesDeDocument {

    @Override
    public List<TypeDeDocumentDeclare> types() {
        return List.of(new TypeDeDocumentDeclare(ProcesVerbalRenduParDocuments.TYPE_DE_DOCUMENT, "Procès-verbal d'inventaire de stock",
                "reports/inventaire_stock.jrxml", List.of(), 1,
                "Inventaire physique : détail par article et par lot, écarts, validation"));
    }
}
