package com.hemodialyse.backend.contexts.dossiermedical.domain;

/** Le format d'export demandé n'est ni « pdf » ni « csv » : 400 {@code INVALID_EXPORT_FORMAT} (code reproduit du legacy). */
public class FormatDExportInconnu extends SaisieInvalide {
    public FormatDExportInconnu() {
        super("format doit être pdf ou csv");
    }
}
