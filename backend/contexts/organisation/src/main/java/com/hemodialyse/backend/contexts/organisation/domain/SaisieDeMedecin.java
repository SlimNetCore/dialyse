package com.hemodialyse.backend.contexts.organisation.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Le nom, le prénom et la spécialité d'un médecin, contrôlés : nettoyés (espaces, espace insécable ; vide = absent), nom obligatoire,
 * chaque champ borné à 255 caractères. Impossible à construire invalide ({@link #controler}) ; mêmes anomalies, codes et messages que
 * l'administration des référentiels (PL11-0).
 */
public record SaisieDeMedecin(String nom, String prenom, String specialite) {

    public static final int LONGUEUR_MAX = 255;

    public static SaisieDeMedecin controler(String nom, String prenom, String specialite) {
        List<AnomalieDeSaisie> anomalies = new ArrayList<>();
        String n = champ("nom", "Nom", nom, true, anomalies);
        String p = champ("prenom", "Prénom", prenom, false, anomalies);
        String s = champ("specialite", "Spécialité", specialite, false, anomalies);
        if (!anomalies.isEmpty()) throw new SaisieDeMedecinInvalide(anomalies);
        return new SaisieDeMedecin(n, p, s);
    }

    private static String champ(String cle, String libelleDuChamp, String brut, boolean obligatoire, List<AnomalieDeSaisie> anomalies) {
        String valeur = brut == null ? null : brut.replace(' ', ' ').trim();
        if (valeur != null && valeur.isEmpty()) valeur = null;
        if (valeur == null) {
            if (obligatoire) {
                anomalies.add(new AnomalieDeSaisie(cle, "REQUIRED", "« " + libelleDuChamp + " » est obligatoire.", Map.of()));
            }
            return null;
        }
        if (valeur.length() > LONGUEUR_MAX) {
            anomalies.add(new AnomalieDeSaisie(cle, "TOO_LONG",
                    "« " + libelleDuChamp + " » dépasse " + LONGUEUR_MAX + " caractères (" + valeur.length() + ").",
                    Map.of("max", String.valueOf(LONGUEUR_MAX), "length", String.valueOf(valeur.length()))));
            return null;
        }
        return valeur;
    }

    /** Clé d'unicité : nom et prénom (absent = vide), casse et espaces ignorés. */
    public static String cleDe(String nom, String prenom) {
        return normaliser(nom) + "|" + normaliser(prenom);
    }

    private static String normaliser(String texte) {
        return texte == null ? "" : texte.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public String cle() {
        return cleDe(nom, prenom);
    }
}
