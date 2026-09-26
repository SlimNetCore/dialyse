package com.hemodialyse.backend.domain.organisation.model;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Value Object — coordonnées de contact d'une société ou d'un centre (imprimées dans l'en-tête et le pied de
 * page des documents). Chaque champ est facultatif ; un champ renseigné est validé et normalisé.
 */
public record Coordonnees(String adresse, String ville, String wilaya, String telephone, String email,
                          String siteWeb) {

    public static final Coordonnees VIDES = new Coordonnees(null, null, null, null, null, null);
    private static final Pattern PHONE = Pattern.compile("\\+?[0-9 .()\\-]{6,25}");
    private static final Pattern EMAIL = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern WEB = Pattern.compile("^(https?://)?[A-Z0-9][A-Z0-9.-]*\\.[A-Z]{2,}(/\\S*)?$", Pattern.CASE_INSENSITIVE);

    public Coordonnees {
        adresse = clean(adresse, 250, "adresse");
        ville = clean(ville, 100, "ville");
        wilaya = clean(wilaya, 100, "wilaya");
        telephone = clean(telephone, 30, "téléphone");
        email = clean(email, 150, "email");
        siteWeb = clean(siteWeb, 200, "site web");
        if (telephone != null && !PHONE.matcher(telephone).matches()) {
            throw new BusinessException("COORDONNEES_TELEPHONE_INVALIDE", "Numéro de téléphone invalide");
        }
        if (email != null) {
            email = email.toLowerCase(Locale.ROOT);
            if (!EMAIL.matcher(email).matches()) {
                throw new BusinessException("COORDONNEES_EMAIL_INVALIDE", "Adresse email invalide");
            }
        }
        if (siteWeb != null && !WEB.matcher(siteWeb).matches()) {
            throw new BusinessException("COORDONNEES_SITE_WEB_INVALIDE", "Adresse de site web invalide");
        }
    }

    private static String clean(String value, int max, String label) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        if (trimmed.length() > max) {
            throw new BusinessException("COORDONNEES_TROP_LONG", "Le champ « " + label + " » dépasse " + max + " caractères");
        }
        return trimmed;
    }
}
