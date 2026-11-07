package com.hemodialyse.backend.contexts.seance.domain;

/**
 * Statuts de la colonne {@code seances.statut}, ceux du LEGACY (ADR 0024 : refactoring sur place). {@code ABSENT} est un reliquat
 * historique, lisible mais jamais créé (ADR 0002 : il sort avec Facturation et Absences).
 */
public enum StatutDeSeance {
    CREE, VALIDEE, SIGNEE, FACTUREE, ABSENT;

    /** Une séance réalisée est validée, signée ou facturée (le patient était présent). */
    public boolean estRealisee() {
        return this == VALIDEE || this == SIGNEE || this == FACTUREE;
    }
}
