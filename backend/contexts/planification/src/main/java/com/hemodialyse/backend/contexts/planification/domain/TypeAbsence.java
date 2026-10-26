package com.hemodialyse.backend.contexts.planification.domain;

/**
 * Nature d'une absence d'infirmier. Les noms sont ceux du JSON historique (majuscules exactes exigées).
 */
public enum TypeAbsence {
    CONGE,
    MALADIE,
    FORMATION,
    AUTRE;

    /**
     * Le type saisi ; absent ou inconnu (y compris en minuscules) : {@link SaisieInvalide}.
     */
    public static TypeAbsence depuisLaSaisie(String saisie) {
        if (saisie == null || saisie.isBlank()) {
            throw new SaisieInvalide("Champ obligatoire absent : type");
        }
        try {
            return valueOf(saisie);
        } catch (IllegalArgumentException e) {
            throw new SaisieInvalide("Type d'absence inconnu");
        }
    }
}
