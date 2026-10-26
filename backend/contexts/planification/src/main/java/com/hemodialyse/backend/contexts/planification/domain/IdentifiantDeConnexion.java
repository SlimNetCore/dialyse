package com.hemodialyse.backend.contexts.planification.domain;

import java.util.regex.Pattern;

/**
 * Identifiant de connexion d'un compte créé depuis une fiche : 3 à 50 caractères parmi lettres, chiffres, point, tiret et
 * souligné (RG-INF-010). Refus 422 {@code COMPTE_IDENTIFIANT_INVALIDE}.
 */
public record IdentifiantDeConnexion(String valeur) {
    private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9._-]{3,50}");

    public IdentifiantDeConnexion {
        if (valeur == null || !FORMAT.matcher(valeur).matches()) {
            throw new RegleDePlanificationViolee("COMPTE_IDENTIFIANT_INVALIDE",
                    "Identifiant invalide (3 à 50 caractères : lettres, chiffres, point, tiret, souligné)");
        }
    }

    /** L'identifiant saisi, espaces de bord retirés. */
    public static IdentifiantDeConnexion depuisLaSaisie(String saisie) {
        return new IdentifiantDeConnexion(saisie == null ? "" : saisie.trim());
    }
}
