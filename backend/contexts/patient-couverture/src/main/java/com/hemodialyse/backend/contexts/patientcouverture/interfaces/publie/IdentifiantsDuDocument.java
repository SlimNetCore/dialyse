package com.hemodialyse.backend.contexts.patientcouverture.interfaces.publie;

import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Lecture des identifiants que le client joint à une demande d'impression (DOC5h, ADR 0200) : absent ou vide, l'identifiant n'est pas fourni
 * (comme l'ancien {@code NULLIF(..., '')}) ; présent mais illisible, c'est un échec d'impression (500) au texte de l'ancienne erreur SQL
 * ({@code CAST} de l'identifiant), reproduit (KB-DOC5H-2).
 */
final class IdentifiantsDuDocument {

    private IdentifiantsDuDocument() {
    }

    /** @param gabarit le nom du gabarit repris dans le texte historique de l'échec */
    static UUID lire(String brut, String gabarit) {
        if (brut == null || brut.isEmpty()) return null;
        try {
            return UUID.fromString(brut);
        } catch (IllegalArgumentException illisible) {
            throw new IllegalStateException("Error executing SQL statement for: " + gabarit + ".", illisible);
        }
    }

    /** Une date du gabarit : {@code java.sql.Date} comme le champ déclaré, nulle si absente. */
    static Date date(LocalDate d) {
        return d == null ? null : Date.valueOf(d);
    }
}
