package com.hemodialyse.backend.contexts.dossiermedical.domain;

/**
 * Aucune observation avec cet identifiant pour ce patient dans ce centre. Reproduit du legacy (KB-MED-76, à confirmer avec les métiers) :
 * l'interface répond 400 {@code BAD_REQUEST} « Observation introuvable » (pas 404) pour une correction.
 */
public class ObservationIntrouvable extends RuntimeException {
    public ObservationIntrouvable() {
        super("Observation introuvable");
    }
}
