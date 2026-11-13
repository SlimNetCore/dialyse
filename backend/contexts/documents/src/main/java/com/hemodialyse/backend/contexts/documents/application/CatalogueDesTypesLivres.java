package com.hemodialyse.backend.contexts.documents.application;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.DeclarationDesTypesDeDocument;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.TypeDeDocumentDeclare;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Les types de document livrés avec l'application : la réunion des types que chaque contexte propriétaire déclare (DOC4c, ADR 0166), dans
 * l'ordre des déclarations. Chaque type livré est provisionné pour chaque centre ({@code ModeleDocumentProvisioner}) et rendu par le moteur
 * à partir de son gabarit par défaut : le centre peut le désactiver ou en téléverser une version personnalisée. Un code déclaré deux fois
 * est refusé dès la construction (une seule source de vérité par type).
 */
public final class CatalogueDesTypesLivres {

    private final List<TypeDeDocumentDeclare> types;
    private final Map<String, TypeDeDocumentDeclare> parCode = new HashMap<>();

    public CatalogueDesTypesLivres(List<DeclarationDesTypesDeDocument> declarations) {
        List<TypeDeDocumentDeclare> reunis = new ArrayList<>();
        for (DeclarationDesTypesDeDocument declaration : declarations) {
            for (TypeDeDocumentDeclare type : declaration.types()) {
                if (parCode.put(type.code(), type) != null) {
                    throw new IllegalStateException("Le type de document " + type.code() + " est déclaré plus d'une fois");
                }
                reunis.add(type);
            }
        }
        this.types = List.copyOf(reunis);
    }

    public List<TypeDeDocumentDeclare> types() {
        return types;
    }

    /** Le type livré de ce code (sans casse ni espaces autour), s'il est déclaré. */
    public Optional<TypeDeDocumentDeclare> trouver(String type) {
        if (type == null) return Optional.empty();
        return Optional.ofNullable(parCode.get(type.trim().toUpperCase(Locale.ROOT)));
    }
}
