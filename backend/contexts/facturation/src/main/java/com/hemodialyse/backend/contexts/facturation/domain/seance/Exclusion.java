package com.hemodialyse.backend.contexts.facturation.domain.seance;

import com.hemodialyse.backend.contexts.facturation.domain.simulation.StatutDeSeance;

/**
 * Décision d'exclure une séance de la facturation : la séance passe à {@code ABSENT}, son statut d'avant est mémorisé pour la
 * réinclusion, et une absence de patient est déclarée avec ce motif.
 *
 * @param commentaire nettoyé ; nul s'il n'y en a pas
 */
public record Exclusion(StatutDeSeance statutAvant, MotifDAbsence motif, String commentaire) {
}
