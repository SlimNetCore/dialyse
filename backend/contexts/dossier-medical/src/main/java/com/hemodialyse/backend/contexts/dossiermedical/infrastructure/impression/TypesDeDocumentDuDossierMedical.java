package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.impression;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.DeclarationDesTypesDeDocument;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.TypeDeDocumentDeclare;

import java.util.List;

/** Le type de document que le Dossier médical possède (documents.v1, DOC4c, ADR 0166) : l'ordonnance médicamenteuse. */
public final class TypesDeDocumentDuDossierMedical implements DeclarationDesTypesDeDocument {

    public static final String ORDONNANCE = "ORDONNANCE";

    @Override
    public List<TypeDeDocumentDeclare> types() {
        return List.of(new TypeDeDocumentDeclare(ORDONNANCE, "Ordonnance médicamenteuse", "reports/ordonnance.jrxml", List.of(), 1,
                "Ordonnance signée par le médecin, imprimable pour le patient"));
    }
}
