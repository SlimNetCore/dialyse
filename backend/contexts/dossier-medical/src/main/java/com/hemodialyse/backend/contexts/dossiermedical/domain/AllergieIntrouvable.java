package com.hemodialyse.backend.contexts.dossiermedical.domain;

/**
 * Aucune allergie avec cet identifiant pour ce patient dans ce centre. Reproduit du legacy (KB-MED-60, à confirmer avec les métiers) :
 * l'interface répond 400 {@code BAD_REQUEST} (pas 404) pour une correction.
 */
public class AllergieIntrouvable extends RuntimeException {
    public AllergieIntrouvable() {
        super("Allergie introuvable");
    }
}
