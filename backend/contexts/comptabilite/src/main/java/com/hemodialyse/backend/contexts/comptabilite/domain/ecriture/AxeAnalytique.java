package com.hemodialyse.backend.contexts.comptabilite.domain.ecriture;

import java.util.Locale;

/**
 * Axe analytique porté par une ligne d'écriture : un code (en majuscules) et un libellé, tous deux obligatoires.
 */
public record AxeAnalytique(String code, String libelle) {

    public AxeAnalytique {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Le code d'axe analytique est obligatoire");
        }
        if (libelle == null || libelle.isBlank()) {
            throw new IllegalArgumentException("Le libelle d'axe analytique est obligatoire");
        }
        code = code.trim().toUpperCase(Locale.ROOT);
        libelle = libelle.trim();
    }
}
