package com.hemodialyse.backend.contexts.patientcouverture.domain;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Le contrôle d'une saisie de payeur, champ après champ, dans l'ordre des champs du niveau : chaque anomalie est collectée (toutes sont
 * rendues ensemble par {@link #verifier()}). Mêmes règles et mêmes messages que l'administration actuelle des référentiels.
 */
final class ControleDeSaisieDePayeur {

    static final int CODE_MAX = 50;
    static final int TEXTE_MAX = 255;
    private static final List<String> TYPES_DE_CAISSE = List.of("STANDARD", "VACANCIER");

    private final List<AnomalieDeSaisie> anomalies = new ArrayList<>();

    /** La valeur sans espaces extrêmes ni espaces insécables extrêmes ; {@code null} si vide. */
    static String nettoyer(String valeur) {
        if (valeur == null) return null;
        String propre = valeur.replace(' ', ' ').trim();
        return propre.isEmpty() ? null : propre;
    }

    /** Un texte : obligatoire ou non, de {@code max} caractères au plus ; rend la valeur nettoyée, ou {@code null} si absente ou refusée. */
    String texte(String champ, String libelle, int max, boolean obligatoire, String brut) {
        String valeur = nettoyer(brut);
        if (valeur == null) {
            if (obligatoire) anomalies.add(new AnomalieDeSaisie(champ, "REQUIRED", "« " + libelle + " » est obligatoire.", Map.of()));
            return null;
        }
        if (valeur.length() > max) {
            anomalies.add(new AnomalieDeSaisie(champ, "TOO_LONG",
                    "« " + libelle + " » dépasse " + max + " caractères (" + valeur.length() + ").",
                    Map.of("max", String.valueOf(max), "length", String.valueOf(valeur.length()))));
            return null;
        }
        return valeur;
    }

    /** Le type de caisse : STANDARD à défaut ; casse, accents, espaces et tirets sont normalisés ; une autre valeur est refusée. */
    String typeDeCaisse(String brut) {
        String valeur = nettoyer(brut);
        if (valeur == null) return "STANDARD";
        String sansAccents = Normalizer.normalize(valeur, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String code = sansAccents.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s\\-]+", "_");
        if (!TYPES_DE_CAISSE.contains(code)) {
            String autorises = String.join(", ", TYPES_DE_CAISSE);
            anomalies.add(new AnomalieDeSaisie("typeCaisse", "INVALID_VALUE",
                    "« Type de caisse » : « " + valeur + " » n'est pas une valeur autorisée (" + autorises + ").",
                    Map.of("value", valeur, "allowed", autorises)));
            return null;
        }
        return code;
    }

    /** Le parent obligatoire, désigné par son code ou son identifiant dans la liste du centre ; rend son identifiant ou {@code null}. */
    UUID parent(String champ, String libelle, NiveauDePayeur cible, String brut, List<? extends Payeur> parentsDuCentre) {
        String valeur = nettoyer(brut);
        if (valeur == null) {
            anomalies.add(new AnomalieDeSaisie(champ, "REQUIRED", "« " + libelle + " » est obligatoire.", Map.of()));
            return null;
        }
        return Payeur.resoudre(valeur, parentsDuCentre).orElseGet(() -> {
            anomalies.add(new AnomalieDeSaisie(champ, "REFERENCE_NOT_FOUND",
                    "« " + libelle + " » : « " + valeur + " » est introuvable dans « " + cible.libelle()
                            + " » — créez-le d'abord (ou importez d'abord le fichier « " + cible.libelle() + " »).",
                    Map.of("value", valeur, "target", cible.designation())));
            return null;
        });
    }

    /** Refuse la saisie avec toutes ses anomalies, s'il y en a. */
    void verifier() {
        if (!anomalies.isEmpty()) throw new SaisieDePayeurInvalide(anomalies);
    }
}
