package com.hemodialyse.backend.contexts.facturation.publishedlanguage;

import java.util.Locale;

/**
 * Format de sortie de la synthèse : absent ou blanc = PDF ; la casse et les espaces sont ignorés. {@code XLS}, {@code XLSX} et
 * {@code EXCEL} livrent le même classeur ; {@code HTML} est un PDF embarqué (KB-FAC-59 reproduit).
 */
public enum FormatDImpression {
    PDF, EXCEL, XLS, XLSX, HTML;

    /**
     * @throws IllegalArgumentException « format non supporte: X » pour un format inconnu
     */
    public static FormatDImpression depuis(String brut) {
        String normalise = brut == null || brut.isBlank() ? "PDF" : brut.trim().toUpperCase(Locale.ROOT);
        try {
            return valueOf(normalise);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("format non supporte: " + normalise);
        }
    }

    public boolean estUnClasseur() {
        return this == EXCEL || this == XLS || this == XLSX;
    }
}
