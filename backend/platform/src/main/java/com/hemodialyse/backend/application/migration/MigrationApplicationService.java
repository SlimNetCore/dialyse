package com.hemodialyse.backend.application.migration;

import com.hemodialyse.backend.domain.migration.model.EntityRun;
import com.hemodialyse.backend.domain.migration.model.MigrationBatch;
import com.hemodialyse.backend.domain.migration.model.MigrationEntity;
import com.hemodialyse.backend.domain.migration.port.MigrationUseCase;
import com.hemodialyse.backend.domain.referential.admin.model.ImportTable;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestration transactionnelle de la reprise : un fichier est importé en entier ou pas du tout, une annulation
 * aussi. Les écritures de reprise contournant les écrans, les caches patients / assurés sont vidés après commit.
 */
@Service
public class MigrationApplicationService {

    /**
     * Caches alimentés par les données reprises (clés non préfixées par le centre : vidage complet).
     */
    static final List<String> PATIENT_CACHES = List.of("patient.byId", "patient.byNumeroAssurance", "patient.byCenter",
            "patient.countByCenter", "patient.list.summary", "patient.list.summary.details", "patient.assure.byNumero",
            "patient.assure.searchByCenter", "patient.assignment.primary", "patient.assignment.history");

    private final MigrationUseCase useCase;
    private final CacheManager cacheManager;

    public MigrationApplicationService(MigrationUseCase useCase, CacheManager cacheManager) {
        this.useCase = useCase;
        this.cacheManager = cacheManager;
    }

    @Transactional
    public MigrationBatch open(CenterId centerId, String libelle, String sourceSystem, LocalDate dateDebutReprise, String user) {
        return useCase.open(centerId, libelle, sourceSystem, dateDebutReprise, user);
    }

    @Transactional(readOnly = true)
    public PagedResult<MigrationBatch> list(CenterId centerId, int page, int size) {
        return useCase.list(centerId, page, size);
    }

    @Transactional(readOnly = true)
    public MigrationUseCase.BatchDetail get(CenterId centerId, UUID batchId) {
        return useCase.get(centerId, batchId);
    }

    @Transactional
    public EntityRun importEntity(CenterId centerId, UUID batchId, MigrationEntity entity, String fileName,
                                  ImportTable table, boolean dryRun, String user) {
        EntityRun run = useCase.importEntity(centerId, batchId, entity, fileName, table, dryRun, user);
        if (run.applied()) clearCachesAfterCommit();
        return run;
    }

    @Transactional
    public MigrationBatch close(CenterId centerId, UUID batchId) {
        return useCase.close(centerId, batchId);
    }

    @Transactional
    public MigrationBatch cancel(CenterId centerId, UUID batchId) {
        MigrationBatch batch = useCase.cancel(centerId, batchId);
        clearCachesAfterCommit();
        return batch;
    }

    @Transactional(readOnly = true)
    public Map<String, Map<String, String>> valueMappings(CenterId centerId) {
        return useCase.valueMappings(centerId);
    }

    @Transactional
    public void saveValueMapping(CenterId centerId, String column, String source, String target) {
        useCase.saveValueMapping(centerId, column, source, target);
    }

    @Transactional
    public void deleteValueMapping(CenterId centerId, String column, String source) {
        useCase.deleteValueMapping(centerId, column, source);
    }

    private void clearCachesAfterCommit() {
        Runnable clear = () -> PATIENT_CACHES.forEach(name -> {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) cache.clear();
        });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    clear.run();
                }
            });
        } else {
            clear.run();
        }
    }
}

