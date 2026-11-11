package com.hemodialyse.backend.infrastructure.migration.port;

import com.hemodialyse.backend.infrastructure.migration.model.EntityRun;
import com.hemodialyse.backend.infrastructure.migration.model.MigrationBatch;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port Out — persistance des lots de reprise et de leurs comptes rendus.
 */
public interface MigrationBatchRepositoryPort {

    MigrationBatch save(MigrationBatch batch);

    Optional<MigrationBatch> findById(CentreId centerId, UUID batchId);

    Optional<MigrationBatch> findActive(CentreId centerId);

    PagedResult<MigrationBatch> findPaged(CentreId centerId, int page, int size);

    void saveRun(CentreId centerId, UUID batchId, EntityRun run);

    /**
     * Dernier compte rendu de chaque donnée reprise du lot.
     */
    List<EntityRun> findLatestRuns(CentreId centerId, UUID batchId);
}

