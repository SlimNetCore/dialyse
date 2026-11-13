package com.hemodialyse.backend.contexts.seance.infrastructure.acl.documents;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.DeclarationDesTypesDeDocument;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.TypeDeDocumentDeclare;

import java.util.List;

/** Le type de document que la Séance possède (documents.v1, DOC4c, ADR 0166) : le cahier de dialyse. */
public final class TypesDeDocumentDeLaSeance implements DeclarationDesTypesDeDocument {

    @Override
    public List<TypeDeDocumentDeclare> types() {
        return List.of(new TypeDeDocumentDeclare(CahierRenduParDocuments.TYPE_DE_DOCUMENT, "Cahier de dialyse",
                "reports/cahier_dialyse.jrxml", List.of(), 1,
                "Cahier de dialyse d'un patient : une page par séance (paramédical, consommables valorisés, dossier et prescription médicale)"));
    }
}
