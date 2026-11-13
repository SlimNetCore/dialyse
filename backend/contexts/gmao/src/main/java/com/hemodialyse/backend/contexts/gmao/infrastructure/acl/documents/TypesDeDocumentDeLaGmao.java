package com.hemodialyse.backend.contexts.gmao.infrastructure.acl.documents;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.DeclarationDesTypesDeDocument;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.TypeDeDocumentDeclare;

import java.util.List;

/** Le type de document que la GMAO possède (documents.v1, DOC4c, ADR 0166) : le bon d'intervention. */
public final class TypesDeDocumentDeLaGmao implements DeclarationDesTypesDeDocument {

    @Override
    public List<TypeDeDocumentDeclare> types() {
        return List.of(new TypeDeDocumentDeclare(BonRenduParDocuments.TYPE_DE_DOCUMENT, "Bon d'intervention (GMAO)",
                "reports/bon_intervention.jrxml", List.of(), 1,
                "Bon d'intervention de maintenance : équipement, période, états, travaux, coûts, signatures"));
    }
}
