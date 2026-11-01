package com.hemodialyse.backend.contexts.organisation.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Value object : coordonnées de contact d'une société ou d'un centre (imprimées dans
 * l'en-tête et le pied de page des documents). Chaque champ est facultatif ; un champ
 * renseigné est validé et normalisé (règles RG-ORG-020 à RG-ORG-022).
 */
public record Coordonnees(String adresse, String ville, String wilaya, String telephone,
                          String email, String siteWeb) {

    public static final Coordonnees VIDES = new Coordonnees(null, null, null, null, null, null);

    private static final Pattern TELEPHONE = Pattern.compile("\\+?[0-9 .()\\-]{6,25}");
    private static final Pattern EMAIL = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SITE_WEB = Pattern.compile("^(https?://)?[A-Z0-9][A-Z0-9.-]*\\.[A-Z]{2,}(/\\S*)?$",
            Pattern.CASE_INSENSITIVE);

    public Coordonnees {
        adresse = nettoyer(adresse, 250, "adresse");
        ville = nettoyer(ville, 100, "ville");
        wilaya = nettoyer(wilaya, 100, "wilaya");
        telephone = nettoyer(telephone, 30, "telephone");
        email = nettoyer(email, 150, "email");
        siteWeb = nettoyer(siteWeb, 200, "site web");
        if (telephone != null && !TELEPHONE.matcher(telephone).matches()) {
            throw new IllegalArgumentException("COORDONNEES_TELEPHONE_INVALIDE");
        }
        if (email != null) {
            email = email.toLowerCase(Locale.ROOT);
            if (!EMAIL.matcher(email).matches()) {
                throw new IllegalArgumentException("COORDONNEES_EMAIL_INVALIDE");
            }
        }
        if (siteWeb != null && !SITE_WEB.matcher(siteWeb).matches()) {
            throw new IllegalArgumentException("COORDONNEES_SITE_WEB_INVALIDE");
        }
    }

    private static String nettoyer(String valeur, int max, String libelle) {
        if (valeur == null) return null;
        String normalisee = valeur.trim();
        if (normalisee.isEmpty()) return null;
        if (normalisee.length() > max) {
            throw new IllegalArgumentException("COORDONNEES_TROP_LONG");
        }
        return normalisee;
    }
}
