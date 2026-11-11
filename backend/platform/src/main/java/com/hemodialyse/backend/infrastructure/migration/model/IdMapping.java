package com.hemodialyse.backend.infrastructure.migration.model;

/**
 * Correspondance « identifiant d'origine → identifiant cible » d'une donnée reprise.
 *
 * @param entity      donnée reprise
 * @param legacyId    identifiant dans l'ancien système
 * @param targetId    identifiant dans la plateforme (UUID ou n° d'assurance pour un assuré)
 * @param operation   CREATED si la donnée a été créée par la reprise (supprimable à l'annulation du lot),
 *                    UPDATED si elle existait déjà (rapprochement : jamais supprimée)
 * @param etatOrigine pour un assuré rapproché (UPDATED) : son état juste avant la première reprise, mémorisé par Patient et couverture
 *                    (opaque pour l'outil) pour que l'annulation du lot le restaure (KB-S6a-2, ADR 0134) ; nul sinon, et pour les
 *                    correspondances antérieures à S6b-3 (leur annulation ne restaure rien, comme avant)
 */
public record IdMapping(MigrationEntity entity, String legacyId, String targetId, Operation operation, String etatOrigine) {

    public IdMapping(MigrationEntity entity, String legacyId, String targetId, Operation operation) {
        this(entity, legacyId, targetId, operation, null);
    }

    public enum Operation {CREATED, UPDATED}
}
