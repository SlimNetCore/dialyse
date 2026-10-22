package com.hemodialyse.backend.contexts.absencespatients.domain;

/**
 * Synthèse du suivi d'un centre : absences encore à qualifier et, parmi elles, celles en retard (RG-ABS-043).
 */
public record SyntheseDesAbsences(long aQualifier, long enRetard) {
}
