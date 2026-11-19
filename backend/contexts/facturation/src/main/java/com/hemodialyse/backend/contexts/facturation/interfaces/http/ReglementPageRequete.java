package com.hemodialyse.backend.contexts.facturation.interfaces.http;

import com.hemodialyse.backend.contexts.facturation.application.reglement.pagination.PaginationInvalide;

import java.util.Map;
import java.util.UUID;

/**
 * Lecture stricte des paramètres de {@code GET /api/v1/reglements/page} : un paramètre illisible ou obligatoire absent est
 * refusé en 400 {@code PARAMETRE_INVALIDE} (contrat {@code pagination-serveur}). Les paramètres sont lus dans le corps de la
 * méthode, après le {@code @PreAuthorize} : un appelant sans rôle reçoit 403 avant toute validation.
 */
final class ReglementPageRequete {

    private final Map<String, String> parametres;

    ReglementPageRequete(Map<String, String> parametres) {
        this.parametres = parametres;
    }

    Map<String, String> parametres() {
        return parametres;
    }

    String texte(String nom) {
        String valeur = parametres.get(nom);
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    UUID uuidObligatoire(String nom) {
        UUID valeur = uuid(nom);
        if (valeur == null) {
            throw absent(nom);
        }
        return valeur;
    }

    UUID uuid(String nom) {
        String valeur = texte(nom);
        if (valeur == null) return null;
        try {
            return UUID.fromString(valeur);
        } catch (IllegalArgumentException e) {
            throw illisible(nom);
        }
    }

    int entierObligatoire(String nom, int minimum, int maximum) {
        Integer valeur = entier(nom, minimum, maximum);
        if (valeur == null) {
            throw absent(nom);
        }
        return valeur;
    }

    Integer entier(String nom, int minimum, int maximum) {
        Integer valeur = entier(nom);
        if (valeur != null && (valeur < minimum || valeur > maximum)) {
            throw new PaginationInvalide(PaginationInvalide.PARAMETRE_INVALIDE,
                    "Le paramètre « " + nom + " » doit etre compris entre " + minimum + " et " + maximum);
        }
        return valeur;
    }

    Integer entier(String nom) {
        String valeur = texte(nom);
        if (valeur == null) return null;
        try {
            return Integer.valueOf(valeur);
        } catch (NumberFormatException e) {
            throw illisible(nom);
        }
    }

    private static PaginationInvalide illisible(String nom) {
        return new PaginationInvalide(PaginationInvalide.PARAMETRE_INVALIDE, "Le paramètre « " + nom + " » est illisible");
    }

    private static PaginationInvalide absent(String nom) {
        return new PaginationInvalide(PaginationInvalide.PARAMETRE_INVALIDE, "Le paramètre « " + nom + " » est obligatoire");
    }
}
