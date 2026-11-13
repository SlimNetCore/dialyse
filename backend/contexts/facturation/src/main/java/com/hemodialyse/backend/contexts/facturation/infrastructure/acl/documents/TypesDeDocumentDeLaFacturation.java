package com.hemodialyse.backend.contexts.facturation.infrastructure.acl.documents;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.DeclarationDesTypesDeDocument;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.TypeDeDocumentDeclare;

import java.util.List;

/** Le type de document que la Facturation possède (documents.v1, DOC4c, ADR 0166) : la synthèse mensuelle de facturation. */
public final class TypesDeDocumentDeLaFacturation implements DeclarationDesTypesDeDocument {

    @Override
    public List<TypeDeDocumentDeclare> types() {
        return List.of(new TypeDeDocumentDeclare(SyntheseRendueParDocuments.TYPE_DE_DOCUMENT, "Synthèse mensuelle facturation",
                "reports/synthese_mensuelle_facturation.jrxml", List.of(), 1,
                "Tableau croisé facturation par forfait/caisse avec répartition graphique"));
    }
}
