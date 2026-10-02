package com.hemodialyse.backend.domain.absence.model;

/**
 * Origine d'une absence : détectée par le contrôle nocturne (séance prévue sans séance réalisée) ou déclarée par un
 * utilisateur.
 */
public enum SourceAbsence {
    AUTOMATIQUE,
    DECLAREE
}
