package com.hemodialyse.backend.domain.migration.service;

import com.hemodialyse.backend.domain.migration.model.MigrationEntity;
import com.hemodialyse.backend.domain.referential.admin.model.ValidationIssue;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.List;
import java.util.Map;

/**
 * Règles propres à une donnée historique rattachée à un patient, appliquées par {@link PatientRecordMigrator}.
 * Une implémentation par donnée (attestations, prises en charge, séances…).
 */
public interface RecordRules {

    MigrationEntity entity();

    /**
     * Valeurs (hors patient) qui identifient une ligne déjà présente : rejouer un fichier la met à jour.
     * Une clé vide = une seule ligne par patient (ex. dossier médical).
     */
    Map<String, Object> key(EntityMigrator.Row row);

    /**
     * Champ date comparé au début de la période reprise : les lignes antérieures sont ignorées. {@code null} = pas de filtre.
     */
    default String periodField() {
        return null;
    }

    /**
     * Une ligne déjà présente peut-elle être mise à jour ? Sinon elle est conservée telle quelle.
     */
    default boolean updatable() {
        return true;
    }

    /**
     * Appelé une fois par fichier (chargement des référentiels utiles…).
     */
    default void begin(CenterId centerId) {
    }

    /**
     * Contrôles propres à la donnée.
     */
    default void check(EntityMigrator.Row row, List<ValidationIssue> errors, List<ValidationIssue> warnings) {
    }

    /**
     * Valeurs à écrire (hors patient) ; les valeurs {@code null} ne sont jamais écrites.
     */
    Map<String, Object> values(EntityMigrator.Row row);
}

