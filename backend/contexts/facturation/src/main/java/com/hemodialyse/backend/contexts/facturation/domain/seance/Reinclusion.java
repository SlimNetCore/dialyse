package com.hemodialyse.backend.contexts.facturation.domain.seance;

import com.hemodialyse.backend.contexts.facturation.domain.simulation.StatutDeSeance;

import java.util.UUID;

/**
 * Décision de réinclure une séance exclue : elle retrouve son statut d'avant l'exclusion et l'absence que l'exclusion avait
 * déclarée est retirée.
 *
 * @param absenceARetirer nulle si l'exclusion n'a pas gardé l'identité de son absence
 */
public record Reinclusion(StatutDeSeance statutRetabli, UUID absenceARetirer) {
}
