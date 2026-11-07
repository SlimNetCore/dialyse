package com.hemodialyse.backend.contexts.dossiermedical.domain;

/**
 * Aucune demande d'examen avec cet identifiant pour ce patient dans ce centre. Reproduit du legacy : l'interface répond 400
 * {@code BAD_REQUEST} (pas 404), contrat conservé tant que les métiers et le frontend n'ont pas décidé (KB-MED-39, ADR 0082).
 */
public class DemandeExamenIntrouvable extends RuntimeException {
    public DemandeExamenIntrouvable() {
        super("Demande d'examen introuvable");
    }
}
