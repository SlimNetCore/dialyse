package com.hemodialyse.backend.contexts.documents.domain;

import java.util.Locale;

/** Le format dans lequel un document est servi. Tout format inconnu (ou vide) est servi en PDF (dette reproduite, KB-DOC1-5 e). */
public enum FormatDeSortie {
    TABLEUR("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
    HTML("text/html", "html"),
    PDF("application/pdf", "pdf");

    private final String typeMime;
    private final String extension;

    FormatDeSortie(String typeMime, String extension) {
        this.typeMime = typeMime;
        this.extension = extension;
    }

    public static FormatDeSortie de(String format) {
        String f = format == null ? "" : format.toUpperCase(Locale.ROOT);
        return switch (f) {
            case "EXCEL", "XLS", "XLSX" -> TABLEUR;
            case "HTML" -> HTML;
            default -> PDF;
        };
    }

    public String typeMime() {
        return typeMime;
    }

    /** Nom du fichier : le type en minuscules, tirets à la place des soulignés, extension du format (ex. {@code liste-patients.xlsx}). */
    public String nomDeFichier(String typeDeDocument) {
        return typeDeDocument.toLowerCase(Locale.ROOT).replace("_", "-") + "." + extension;
    }
}
