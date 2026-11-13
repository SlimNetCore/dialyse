package com.hemodialyse.backend.contexts.documents.publishedlanguage;

import java.util.List;

/**
 * Déclaration de types de document à Documents (documents.v1, ADR 0165) : un contexte qui possède la mise en page d'un type de document
 * publie un bean de ce port ; Documents agrège toutes les déclarations (« Types de documents » proposés à l'écran). Un code de type n'est
 * déclaré qu'une fois : Documents refuse le démarrage sinon.
 */
public interface DeclarationDesTypesDeDocument {

    /** Les types que ce déclarant possède, dans l'ordre où ils doivent être proposés. */
    List<TypeDeDocumentDeclare> types();
}
