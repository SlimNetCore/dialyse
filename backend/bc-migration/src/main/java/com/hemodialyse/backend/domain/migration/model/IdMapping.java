package com.hemodialyse.backend.domain.migration.model;

/**
 * Correspondance « identifiant d'origine → identifiant cible » d'une donnée reprise.
 *
 * @param entity    donnée reprise
 * @param legacyId  identifiant dans l'ancien système
 * @param targetId  identifiant dans la plateforme (UUID ou n° d'assurance pour un assuré)
 * @param operation CREATED si la donnée a été créée par la reprise (supprimable à l'annulation du lot),
 *                  UPDATED si elle existait déjà (rapprochement : jamais supprimée)
 */
public record IdMapping(MigrationEntity entity, String legacyId, String targetId, Operation operation) {

    public enum Operation {CREATED, UPDATED}
}

