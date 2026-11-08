package com.hemodialyse.backend.contexts.seance.domain;

import java.util.Locale;

/** Format d'export du tableau de bord : {@code csv}, {@code pdf} ou {@code xlsx} (casse indifférente). */
public enum FormatDExport {
    CSV("csv", "text/plain"),
    PDF("pdf", "application/pdf"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final String extension;
    private final String typeDeContenu;

    FormatDExport(String extension, String typeDeContenu) {
        this.extension = extension;
        this.typeDeContenu = typeDeContenu;
    }

    public String extension() {
        return extension;
    }

    /** Type de média de la réponse ; le CSV n'annonce aucun jeu de caractères (KB-SEA-67, reproduit). */
    public String typeDeContenu() {
        return typeDeContenu;
    }

    public static FormatDExport lire(String brut) {
        String normalise = brut == null ? "" : brut.toLowerCase(Locale.ROOT);
        for (FormatDExport format : values()) {
            if (format.extension.equals(normalise)) return format;
        }
        throw new SaisieInvalide("format must be 'csv', 'pdf' or 'xlsx'");
    }
}
