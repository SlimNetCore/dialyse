package com.hemodialyse.backend.contexts.patientcouverture.domain;

/** Aucun patient de ce centre n'a cet identifiant : 404 (KB-PAT-18, avant : 400 {@code Patient introuvable}). */
public class PatientIntrouvable extends RuntimeException {
    public PatientIntrouvable() {
        super("Patient introuvable");
    }
}
