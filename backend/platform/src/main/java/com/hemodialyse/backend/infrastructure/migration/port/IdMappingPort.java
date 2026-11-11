package com.hemodialyse.backend.infrastructure.migration.port;

import com.hemodialyse.backend.infrastructure.migration.model.IdMapping;
import com.hemodialyse.backend.infrastructure.migration.model.MigrationEntity;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Port Out — correspondances « identifiant d'origine → identifiant cible », uniques par centre et par donnée.
 */
public interface IdMappingPort {

    Optional<String> findTarget(CentreId centerId, MigrationEntity entity, String legacyId);

    /**
     * identifiant d'origine → identifiant cible, pour tout le centre.
     */
    Map<String, String> findAll(CentreId centerId, MigrationEntity entity);

    /**
     * Crée la correspondance ; si elle existe déjà, seul l'identifiant cible est mis à jour (l'opération d'origine est conservée).
     */
    void save(CentreId centerId, UUID batchId, IdMapping mapping);

    List<IdMapping> findByBatch(CentreId centerId, UUID batchId);

    void deleteByBatch(CentreId centerId, UUID batchId);
}

