package com.hemodialyse.backend.contexts.facturation.publishedlanguage;

import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Événement d'intégration publié quand un administrateur rouvre un mois de facturation clos (RG-FAC-043, ADR 0037). Publié dans la
 * transaction de la réouverture. Aucun consommateur de production pour l'instant.
 *
 * @param centre     le centre dont le mois est rouvert
 * @param mois       le mois rouvert
 * @param rouvertPar l'utilisateur de la session qui a rouvert
 * @param rouverte   l'instant de la réouverture
 * @param motif      le motif donné (obligatoire)
 */
public record PeriodeDeFacturationRouverte(UUID centre, YearMonth mois, UUID rouvertPar, Instant rouverte, String motif) {
}
