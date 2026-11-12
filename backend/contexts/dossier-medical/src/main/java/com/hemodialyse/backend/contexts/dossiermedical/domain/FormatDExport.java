package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.util.Locale;

/** Format de l'export des statistiques d'un patient. Lu sans egard a la casse ni aux espaces autour. */
public enum FormatDExport {
    CSV, PDF;

    public static FormatDExport lire(String brut) {
        String normalise = brut == null ? "" : brut.trim().toLowerCase(Locale.ROOT);
        return switch (normalise) {
            case "csv" -> CSV;
            case "pdf" -> PDF;
            default -> throw new FormatDExportInconnu();
        };
    }
}
