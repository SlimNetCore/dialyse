package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.FormatDExport;

/** Le document exporte : un CSV (texte) ou un PDF (octets), selon le format demande. */
public record ExportDesStatistiques(FormatDExport format, String csv, byte[] pdf) {

    static ExportDesStatistiques enCsv(String csv) {
        return new ExportDesStatistiques(FormatDExport.CSV, csv, null);
    }

    static ExportDesStatistiques enPdf(byte[] pdf) {
        return new ExportDesStatistiques(FormatDExport.PDF, null, pdf);
    }
}
