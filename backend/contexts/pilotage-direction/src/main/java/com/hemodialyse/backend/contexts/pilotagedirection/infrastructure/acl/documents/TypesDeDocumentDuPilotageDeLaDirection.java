package com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.documents;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.DeclarationDesTypesDeDocument;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.TypeDeDocumentDeclare;

import java.util.List;

/** Le type de document que le Pilotage de la direction possède (documents.v1, DOC4c, ADR 0166) : le rapport de synthèse de la direction. */
public final class TypesDeDocumentDuPilotageDeLaDirection implements DeclarationDesTypesDeDocument {

    @Override
    public List<TypeDeDocumentDeclare> types() {
        return List.of(new TypeDeDocumentDeclare(RapportRenduParDocuments.TYPE_DE_DOCUMENT, "Rapport de synthèse de la direction",
                "reports/rapport_direction.jrxml", List.of(), 1,
                "Rapport de la direction : chiffres clés, alertes, finances, évolution mensuelle, qualité des soins, stock et "
                        + "répartitions par centre (agrégats anonymes)"));
    }
}
