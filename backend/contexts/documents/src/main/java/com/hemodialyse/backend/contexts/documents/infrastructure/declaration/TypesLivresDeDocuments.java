package com.hemodialyse.backend.contexts.documents.infrastructure.declaration;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.DeclarationDesTypesDeDocument;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.ModeleDocumentCatalog;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.TypeDeDocumentDeclare;

import java.util.List;

/**
 * Déclaration, par Documents lui-même, des types livrés avec l'application (ADR 0165) : le catalogue des documents livrés reste dans Documents
 * tant que le moteur de rendu et le provisionnement des centres le lisent (description, gabarit) et que le contrat de paramètres de chaque type
 * n'est pas décidé avec ses règles (DOC4c). Les contextes propriétaires (déjà liés au langage publié) peuvent déclarer leurs types par le même
 * port, sans cycle Maven.
 */
public final class TypesLivresDeDocuments implements DeclarationDesTypesDeDocument {

    @Override
    public List<TypeDeDocumentDeclare> types() {
        return ModeleDocumentCatalog.entries().stream()
                .map(e -> new TypeDeDocumentDeclare(e.type(), e.libelle(), e.cheminJrxml(), List.of(), 1))
                .toList();
    }
}
