package com.hemodialyse.backend.contexts.absencespatients.domain;

import java.util.Optional;

/**
 * Commentaire d'une absence : rogné, vide = absent, au plus {@value #LONGUEUR_MAXIMALE} caractères (colonne de la table).
 */
public record Commentaire(String texte) {

    public static final int LONGUEUR_MAXIMALE = 1000;

    public Commentaire {
        if (texte == null || texte.isBlank()) {
            throw new IllegalArgumentException("Un commentaire est un texte non vide");
        }
        if (texte.length() > LONGUEUR_MAXIMALE) {
            throw new SaisieInvalide("Le commentaire dépasse " + LONGUEUR_MAXIMALE + " caractères");
        }
    }

    /**
     * @return vide si le texte est nul ou blanc
     * @throws SaisieInvalide le texte rogné dépasse la longueur maximale
     */
    public static Optional<Commentaire> de(String brut) {
        return brut == null || brut.isBlank() ? Optional.empty() : Optional.of(new Commentaire(brut.trim()));
    }
}
