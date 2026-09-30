package com.hemodialyse.backend.domain.migration.port;

import com.hemodialyse.backend.domain.migration.model.MigrationEntity;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Port Out — écriture de l'historique repris rattaché à un patient (attestations, prises en charge, dossier
 * médical, antécédents, sérologies, abords, analyses, séances), <b>dans son état final</b> : aucun circuit de
 * validation, aucune sortie de stock, aucune notification.
 * <p>
 * Les valeurs sont indexées par champ ({@code patientId}, {@code dateDebut}…) et typées ({@link java.time.LocalDate},
 * {@link UUID}, {@link java.math.BigDecimal}, {@link Boolean}, {@link Integer}, {@link String}) ; l'adaptateur les
 * range dans les colonnes de la donnée. Toutes les opérations sont restreintes au centre.
 */
public interface HistoricalRecordPort {

    /**
     * Ligne du centre ayant exactement ces valeurs (ex. patient + date de séance), s'il y en a une.
     */
    Optional<UUID> findExisting(CenterId centerId, MigrationEntity entity, Map<String, Object> key);

    UUID insert(CenterId centerId, MigrationEntity entity, Map<String, Object> values);

    /**
     * Met à jour les seuls champs fournis (une reprise n'efface jamais une valeur existante).
     */
    void update(CenterId centerId, MigrationEntity entity, UUID id, Map<String, Object> values);
}

