package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import com.hemodialyse.backend.contexts.dossiermedical.domain.SaisieInvalide;

/**
 * Lecture d'une valeur d'énumération du contrat HTTP : absente ou inconnue, elle est refusée en 400 stable, sans jamais révéler le nom
 * d'une classe interne (KB-MED-87 : le legacy répondait 500 pour une valeur absente et exposait {@code No enum constant ...}).
 */
final class Enumerations {
    private Enumerations() {
    }

    static <E extends Enum<E>> E obligatoire(Class<E> type, String valeur, String champ) {
        if (valeur == null) throw new SaisieInvalide("Le champ " + champ + " est obligatoire");
        try {
            return Enum.valueOf(type, valeur);
        } catch (IllegalArgumentException e) {
            throw new SaisieInvalide("Valeur inconnue pour le champ " + champ);
        }
    }
}
