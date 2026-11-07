package com.hemodialyse.backend.contexts.dossiermedical.domain;

/**
 * Aucune sérologie avec cet identifiant pour ce patient dans ce centre. Reproduit du legacy : l'interface répond 400 {@code BAD_REQUEST}
 * (pas 404) pour une correction ; contrat conservé tant que les métiers et le frontend n'ont pas décidé (KB-MED-49).
 */
public class SerologieIntrouvable extends RuntimeException {
    public SerologieIntrouvable() {
        super("Sérologie introuvable");
    }
}
