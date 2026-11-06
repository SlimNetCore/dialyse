package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.util.UUID;

/** Identité d'un patient (colonne {@code seances.patient_id}). Pas d'existence vérifiée : un patient inconnu a un historique vide. */
public record PatientId(UUID valeur) {
    public PatientId {
        if (valeur == null) throw new SaisieInvalide("Patient requis");
    }
}
