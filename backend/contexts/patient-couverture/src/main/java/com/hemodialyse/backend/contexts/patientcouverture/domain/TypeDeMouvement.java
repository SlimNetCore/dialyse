package com.hemodialyse.backend.contexts.patientcouverture.domain;

/**
 * Nature d'un mouvement du patient (colonne {@code mouvement_patient.type}, vocabulaire du legacy conservé : {@code TRANSFERT},
 * {@code GUERISON}). {@code PLACE_LIBEREE} est écrit par la Planification (ADR 0052), jamais par ce contexte.
 */
public enum TypeDeMouvement {
    ADMISSION, SEJOUR_TEMPORAIRE, REPRISE, TRANSFERT, DECES, GREFFE, GUERISON, PLACE_LIBEREE;

    public boolean sortie() {
        return this == TRANSFERT || this == DECES || this == GREFFE || this == GUERISON;
    }
}
