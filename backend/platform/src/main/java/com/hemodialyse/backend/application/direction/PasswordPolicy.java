package com.hemodialyse.backend.application.direction;

import java.util.Locale;

/**
 * Politique de mot de passe des comptes « direction » (ils voient les chiffres de tous les centres d'une société) :
 * 12 caractères minimum, au moins une lettre et un chiffre, et différent de l'identifiant.
 * Classe pure : aucune dépendance Spring ni JPA.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;

    private PasswordPolicy() {
    }

    /**
     * @return le code de l'anomalie (traduit côté interface) ou {@code null} si le mot de passe est accepté
     */
    public static String violation(String password, String username) {
        if (password == null || password.length() < MIN_LENGTH) return "PASSWORD_TOO_SHORT";
        if (password.length() > 100) return "PASSWORD_TOO_LONG";
        boolean letter = password.chars().anyMatch(Character::isLetter);
        boolean digit = password.chars().anyMatch(Character::isDigit);
        if (!letter || !digit) return "PASSWORD_WEAK";
        if (username != null && password.toLowerCase(Locale.ROOT).contains(username.toLowerCase(Locale.ROOT))) {
            return "PASSWORD_CONTAINS_USERNAME";
        }
        return null;
    }
}
