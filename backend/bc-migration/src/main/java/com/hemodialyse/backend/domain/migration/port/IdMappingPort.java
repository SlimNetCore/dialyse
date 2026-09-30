package com.hemodialyse.backend.domain.migration.port;

import com.hemodialyse.backend.domain.migration.model.IdMapping;
import com.hemodialyse.backend.domain.migration.model.MigrationEntity;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Port Out — correspondances « identifiant d'origine → identifiant cible », uniques par centre et par donnée.
 */
public interface IdMappingPort {

    Optional<String> findTarget(CenterId centerId, MigrationEntity entity, String legacyId);

    /**
     * identifiant d'origine → identifiant cible, pour tout le centre.
     */
    Map<String, String> findAll(CenterId centerId, MigrationEntity entity);

    /**
     * Crée la correspondance ; si elle existe déjà, seul l'identifiant cible est mis à jour (l'opération d'origine est conservée).
     */
    void save(CenterId centerId, UUID batchId, IdMapping mapping);

    List<IdMapping> findByBatch(CenterId centerId, UUID batchId);

    void deleteByBatch(CenterId centerId, UUID batchId);
}

