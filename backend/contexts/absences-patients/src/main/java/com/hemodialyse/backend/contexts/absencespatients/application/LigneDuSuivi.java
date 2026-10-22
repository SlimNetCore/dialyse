package com.hemodialyse.backend.contexts.absencespatients.application;

import com.hemodialyse.backend.contexts.absencespatients.domain.AbsenceSuivie;

/**
 * Ligne de la liste du suivi, avec l'indicateur « en retard » calculé au jour courant.
 */
public record LigneDuSuivi(AbsenceSuivie absence, String nomDuPatient, boolean enRetard) {
}
