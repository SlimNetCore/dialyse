package com.hemodialyse.backend.contexts.absencespatients.domain;

/**
 * Une valeur saisie est inutilisable telle quelle (commentaire de plus de {@value Commentaire#LONGUEUR_MAXIMALE} caractères) :
 * 400 {@code BAD_REQUEST} (était une erreur technique 500, KB-ABS-17 aligné).
 */
public class SaisieInvalide extends IllegalArgumentException {
    public SaisieInvalide(String message) {
        super(message);
    }
}
