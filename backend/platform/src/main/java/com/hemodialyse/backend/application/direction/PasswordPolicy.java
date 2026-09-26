package com.hemodialyse.backend.application.direction;

import java.util.Locale;

/**
 * Politique de mot de passe des comptes « direction » (ils voient les chiffres de tous les centres d'une société) :
 * 12 caractères minimum, au moins une lettre et un chiffre, et différent de l'identifiant.
 * Classe pure : aucune dépendance Spring ni JPA.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    /**
     * Le compte propriétaire (SUPERADMIN) porte tous les droits d'édition : politique renforcée.
     */
    public static final int OWNER_MIN_LENGTH = 14;

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

    /**
     * Politique du compte propriétaire : 14 caractères minimum avec majuscule, minuscule, chiffre et symbole, sans
     * l'identifiant. Mêmes codes que {@link #violation}.
     */
    public static String ownerViolation(String password, String username) {
        if (password == null || password.length() < OWNER_MIN_LENGTH) return "PASSWORD_TOO_SHORT";
        if (password.length() > 100) return "PASSWORD_TOO_LONG";
        boolean upper = password.chars().anyMatch(Character::isUpperCase);
        boolean lower = password.chars().anyMatch(Character::isLowerCase);
        boolean digit = password.chars().anyMatch(Character::isDigit);
        boolean symbol = password.chars().anyMatch(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c));
        if (!upper || !lower || !digit || !symbol) return "PASSWORD_WEAK";
        if (username != null && !username.isBlank()
                && password.toLowerCase(Locale.ROOT).contains(username.toLowerCase(Locale.ROOT))) {
            return "PASSWORD_CONTAINS_USERNAME";
        }
        return null;
    }
}
