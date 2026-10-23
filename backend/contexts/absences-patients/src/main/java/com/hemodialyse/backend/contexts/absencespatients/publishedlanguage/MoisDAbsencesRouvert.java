package com.hemodialyse.backend.contexts.absencespatients.publishedlanguage;

import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Événement d'intégration publié quand un administrateur rouvre un mois d'absences clos (A6, ADR 0042). Publié dans la transaction
 * de la réouverture. Aucun consommateur de production pour l'instant.
 *
 * @param centre     le centre dont le mois est rouvert
 * @param mois       le mois rouvert
 * @param rouvertPar l'utilisateur de la session qui a rouvert
 * @param rouvert    l'instant de la réouverture
 * @param motif      le motif donné (obligatoire)
 */
public record MoisDAbsencesRouvert(UUID centre, YearMonth mois, UUID rouvertPar, Instant rouvert, String motif) {
}
