package com.hemodialyse.backend.contexts.dossiermedical.domain;

/**
 * Aucune ordonnance avec cet identifiant pour ce patient dans ce centre. Reproduit du legacy : l'interface répond 400
 * {@code BAD_REQUEST} (pas 404), contrat conservé tant que les métiers et le frontend n'ont pas décidé (ADR 0081).
 */
public class OrdonnanceIntrouvable extends RuntimeException {
    public OrdonnanceIntrouvable() {
        super("Ordonnance introuvable");
    }
}
