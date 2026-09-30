package com.hemodialyse.backend.domain.migration.port;

import com.hemodialyse.backend.domain.migration.model.EntityRun;
import com.hemodialyse.backend.domain.migration.model.MigrationBatch;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port Out — persistance des lots de reprise et de leurs comptes rendus.
 */
public interface MigrationBatchRepositoryPort {

    MigrationBatch save(MigrationBatch batch);

    Optional<MigrationBatch> findById(CenterId centerId, UUID batchId);

    Optional<MigrationBatch> findActive(CenterId centerId);

    PagedResult<MigrationBatch> findPaged(CenterId centerId, int page, int size);

    void saveRun(CenterId centerId, UUID batchId, EntityRun run);

    /**
     * Dernier compte rendu de chaque donnée reprise du lot.
     */
    List<EntityRun> findLatestRuns(CenterId centerId, UUID batchId);
}

