package com.hemodialyse.backend.domain.medical.observation.valueobject;

public enum SourceObservation {
    /**
     * Saisie directe par le médecin via le module Examens.
     */
    SAISIE_DIRECTE,
    /**
     * Projetée automatiquement depuis un {@code ResultatAnalyse} (bilan à colonnes fixes) —
     * réservé à une phase ultérieure ; une observation ainsi marquée est en lecture seule via
     * l'API (voir {@code ObservationBiologique#corriger}).
     */
    DERIVEE_BILAN,
    IMPORT
}
