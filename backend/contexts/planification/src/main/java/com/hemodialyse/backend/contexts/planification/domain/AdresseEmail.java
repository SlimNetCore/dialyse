package com.hemodialyse.backend.contexts.planification.domain;

import java.util.regex.Pattern;

/**
 * Adresse e-mail du compte créé depuis une fiche (facultative). Le format est vérifié et la longueur bornée à celle de la colonne
 * (150), KB-PLN-13.
 */
public record AdresseEmail(String valeur) {
    public static final int LONGUEUR_MAXIMALE = 150;
    private static final Pattern FORMAT = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    public AdresseEmail {
        if (valeur == null || valeur.length() > LONGUEUR_MAXIMALE || !FORMAT.matcher(valeur).matches()) {
            throw new SaisieInvalide("Adresse e-mail invalide");
        }
    }

    /** L'adresse saisie, ou {@code null} si elle est absente ou blanche. */
    public static AdresseEmail depuisLaSaisie(String saisie) {
        return saisie == null || saisie.isBlank() ? null : new AdresseEmail(saisie.trim());
    }
}
