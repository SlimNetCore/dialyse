package com.hemodialyse.backend.contexts.absencespatients.interfaces.http.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Réponse de la clôture : l'état du mois et l'avertissement chiffré (absences encore « à qualifier » et, parmi elles, en retard).
 */
public record ClotureReponse(UUID centerId, String mois, String statut, UUID cloturePar, Instant clotureLe,
                             long absencesAQualifierRestantes, long absencesEnRetard) {
}
