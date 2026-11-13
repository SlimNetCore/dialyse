package com.hemodialyse.backend.contexts.documents.application;

import com.hemodialyse.backend.contexts.documents.domain.TypeDisponible;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.DeclarationDesTypesDeDocument;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.TypeDeDocumentDeclare;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Les types de document proposés (DOC4b, ADR 0165) : ceux que les contextes déclarent, dans l'ordre des déclarations, puis le rapport
 * personnalisé que Documents possède. Un même code déclaré deux fois est refusé dès la construction (aucun type ne se déclare en double).
 */
public final class ListerLesTypesDeDocument {

    private final List<TypeDisponible> types;

    public ListerLesTypesDeDocument(List<DeclarationDesTypesDeDocument> declarations) {
        List<TypeDisponible> reunis = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        for (DeclarationDesTypesDeDocument declaration : declarations) {
            for (TypeDeDocumentDeclare type : declaration.types()) {
                if (!codes.add(type.code())) {
                    throw new IllegalStateException("Le type de document " + type.code() + " est déclaré plus d'une fois");
                }
                reunis.add(new TypeDisponible(type.code(), type.libelle()));
            }
        }
        if (!codes.add(TypeDisponible.CODE_DU_RAPPORT_PERSONNALISE)) {
            throw new IllegalStateException("Le code " + TypeDisponible.CODE_DU_RAPPORT_PERSONNALISE + " est réservé au rapport personnalisé");
        }
        reunis.add(TypeDisponible.rapportPersonnalise());
        this.types = List.copyOf(reunis);
    }

    public List<TypeDisponible> lister() {
        return types;
    }
}
