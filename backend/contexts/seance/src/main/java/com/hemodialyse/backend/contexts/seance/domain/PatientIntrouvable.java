package com.hemodialyse.backend.contexts.seance.domain;

/** Le patient n'existe pas dans le centre de la session (inconnu ou d'un autre centre) : 404 {@code PATIENT_INTROUVABLE} (KB-SEA-06). */
public class PatientIntrouvable extends RuntimeException {
    public PatientIntrouvable() {
        super("Patient introuvable dans ce centre");
    }
}
