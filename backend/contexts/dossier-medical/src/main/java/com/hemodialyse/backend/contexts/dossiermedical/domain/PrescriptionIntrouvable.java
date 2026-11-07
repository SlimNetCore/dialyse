package com.hemodialyse.backend.contexts.dossiermedical.domain;

/**
 * Aucune prescription avec cet identifiant pour ce patient dans ce centre : 404 {@code PRESCRIPTION_INTROUVABLE}. Écart décidé
 * (KB-MED-17) : le legacy créait une prescription sur un identifiant inconnu et RÉAFFECTAIT celle d'un autre centre ou d'un autre
 * patient.
 */
public class PrescriptionIntrouvable extends RuntimeException {
    public PrescriptionIntrouvable() {
        super("Prescription introuvable");
    }
}
