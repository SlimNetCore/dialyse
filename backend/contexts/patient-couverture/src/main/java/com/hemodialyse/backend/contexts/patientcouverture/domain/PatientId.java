package com.hemodialyse.backend.contexts.patientcouverture.domain;

import java.util.UUID;

/** Identité d'un patient (colonne {@code patients.id}). */
public record PatientId(UUID valeur) {
    public PatientId {
        if (valeur == null) throw new SaisieInvalide("Patient requis");
    }
}
