package com.hemodialyse.backend.contexts.facturation.interfaces.http;

import jakarta.validation.constraints.NotNull;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Corps de {@code POST /api/v1/facturation/synthese/print} : mêmes champs que le legacy. Les dates sont lues comme du TEXTE ISO
 * ({@code 2026-02-01}, ou date-heure dont l'heure est ignorée, comme avant) : un entier ({@code 20260201}, lu avant comme un nombre de
 * jours depuis 1970) est refusé en 400 (PL8, KB-PL8-6 corrigé).
 */
public record SyntheseRequete(
        @NotNull UUID centerId,
        @NotNull String periodStart,
        @NotNull String periodEnd,
        String format
) {

    public LocalDate debut() {
        return date(periodStart);
    }

    public LocalDate fin() {
        return date(periodEnd);
    }

    private static LocalDate date(String texte) {
        if (texte.isBlank()) {
            // reproduit : une date blanche était lue comme le 1970-01-01 (valeur « vide » du format de date), non décidé (PL8 filet)
            return LocalDate.ofEpochDay(0);
        }
        try {
            String jour = texte.length() > 10 && texte.charAt(10) == 'T' ? texte.substring(0, 10) : texte;
            return LocalDate.parse(jour);
        } catch (DateTimeException e) {
            throw new RequeteIllisible();
        }
    }

    /** Date illisible : 400 sans détail technique. */
    static final class RequeteIllisible extends RuntimeException {
        RequeteIllisible() {
            super("Requête illisible");
        }
    }
}
