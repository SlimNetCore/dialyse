package com.hemodialyse.backend.contexts.absencespatients.domain;

/**
 * Source de l'absence : détectée par le contrôle de nuit ({@code AUTOMATIQUE}) ou déclarée par un utilisateur
 * ({@code DECLAREE}). Valeurs stockées et renvoyées inchangées (RG-ABS-002).
 */
public enum SourceAbsence {
    AUTOMATIQUE,
    DECLAREE
}
