package com.hemodialyse.backend.contexts.documents.domain;

/**
 * Un type de document proposé pour un modèle (« Types de documents ») : son code et son libellé. {@code CUSTOM} est le rapport
 * personnalisé, que Documents seul possède.
 */
public record TypeDisponible(String code, String libelle) {

    public static final String CODE_DU_RAPPORT_PERSONNALISE = "CUSTOM";
    public static final String LIBELLE_DU_RAPPORT_PERSONNALISE = "Rapport personnalisé";

    public TypeDisponible {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Le code d'un type de document est obligatoire");
        if (libelle == null || libelle.isBlank()) throw new IllegalArgumentException("Le libellé d'un type de document est obligatoire");
    }

    public static TypeDisponible rapportPersonnalise() {
        return new TypeDisponible(CODE_DU_RAPPORT_PERSONNALISE, LIBELLE_DU_RAPPORT_PERSONNALISE);
    }
}
