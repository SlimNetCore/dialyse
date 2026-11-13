package com.hemodialyse.backend.contexts.documents.interfaces.http;

import java.util.Locale;
import java.util.UUID;

/**
 * Lecture des paramètres de requête reçus en texte, avec les mêmes conversions qu'avant (valeur vide = absente) mais un paramètre
 * illisible lève {@link ParametreIllisible} (400) au lieu de faire échouer la résolution des arguments (500).
 */
final class Entrees {

    private Entrees() {
    }

    static UUID identifiant(String brut, String nom) {
        if (brut == null || brut.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(brut.trim());
        } catch (IllegalArgumentException e) {
            throw new ParametreIllisible(nom);
        }
    }

    /** Un identifiant requis : absent, vide ou illisible = {@link ParametreIllisible}. */
    static UUID identifiantObligatoire(String brut, String nom) {
        UUID lu = identifiant(brut, nom);
        if (lu == null) {
            throw new ParametreIllisible(nom);
        }
        return lu;
    }

    static Integer entierOuNull(String brut, String nom) {
        if (brut == null || brut.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(brut.trim());
        } catch (NumberFormatException e) {
            throw new ParametreIllisible(nom);
        }
    }

    static int entier(String brut, String nom, int parDefaut) {
        Integer lu = entierOuNull(brut, nom);
        return lu == null ? parDefaut : lu;
    }

    /** Les mêmes valeurs que la conversion de Spring : true/on/yes/1 et false/off/no/0, sans tenir compte de la casse. */
    static boolean booleen(String brut, String nom, boolean parDefaut) {
        if (brut == null || brut.isBlank()) {
            return parDefaut;
        }
        return switch (brut.trim().toLowerCase(Locale.ROOT)) {
            case "true", "on", "yes", "1" -> true;
            case "false", "off", "no", "0" -> false;
            default -> throw new ParametreIllisible(nom);
        };
    }
}
