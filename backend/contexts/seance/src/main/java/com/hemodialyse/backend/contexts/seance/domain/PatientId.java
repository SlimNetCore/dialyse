package com.hemodialyse.backend.contexts.seance.domain;

import java.util.UUID;

/** Identité d'un patient (colonne {@code seances.patient_id}). Référence par identité : l'existence se vérifie par le port des patients. */
public record PatientId(UUID valeur) {
    public PatientId {
        if (valeur == null) throw new SaisieInvalide("Patient requis");
    }
}
