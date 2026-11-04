package com.hemodialyse.backend.contexts.patientcouverture.domain;

/**
 * Aucune attestation de ce centre (et de ce patient, s'il est donné) n'a cet identifiant : 404 {@code ATTESTATION_INTROUVABLE}
 * (avant : suppression « réussie » et diffusion fantôme, KB-PAT-40).
 */
public class AttestationIntrouvable extends RuntimeException {
    public AttestationIntrouvable() {
        super("Attestation introuvable");
    }
}
