package com.hemodialyse.backend.application.auth;

import java.util.Locale;
import java.util.Optional;

/**
 * Règle minimale d'un nouveau mot de passe choisi par l'utilisateur : au moins {@value #LONGUEUR_MIN} caractères, une
 * lettre et un chiffre, et différent de l'identifiant de connexion.
 */
public final class PolitiqueMotDePasse {

    public static final int LONGUEUR_MIN = 10;
    public static final int LONGUEUR_MAX = 128;

    private PolitiqueMotDePasse() {
    }

    /**
     * @return le code d'erreur métier si le mot de passe est refusé, vide s'il est acceptable
     */
    public static Optional<String> refus(String motDePasse, String identifiant) {
        if (motDePasse == null || motDePasse.length() < LONGUEUR_MIN || motDePasse.length() > LONGUEUR_MAX
                || motDePasse.chars().noneMatch(Character::isLetter) || motDePasse.chars().noneMatch(Character::isDigit)
                || (identifiant != null && motDePasse.toLowerCase(Locale.ROOT).contains(identifiant.toLowerCase(Locale.ROOT)))) {
            return Optional.of("PASSWORD_TROP_FAIBLE");
        }
        return Optional.empty();
    }
}
