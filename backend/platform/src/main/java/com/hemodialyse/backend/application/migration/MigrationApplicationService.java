package com.hemodialyse.backend.application.migration;

import com.hemodialyse.backend.infrastructure.migration.model.EntityRun;
import com.hemodialyse.backend.infrastructure.migration.model.MigrationBatch;
import com.hemodialyse.backend.infrastructure.migration.model.MigrationEntity;
import com.hemodialyse.backend.infrastructure.migration.port.MigrationUseCase;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ImportTable;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
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
     * Caches alimentés par les données reprises (clés non préfixées par le centre : vidage complet). Seuls restent ceux que Patient et
     * couverture produit encore : les caches des anciens adapters JPA du patient et de l'assuré (S6b-3, ADR 0134) n'ont plus de producteur.
     */
    static final List<String> PATIENT_CACHES = List.of("patient.list.summary", "patient.list.summary.details",
            "patient.attestation.byPatient", "patient.pec.byPatient", "patient.pec.byCenter");

    private final MigrationUseCase useCase;
    private final CacheManager cacheManager;

    public MigrationApplicationService(MigrationUseCase useCase, CacheManager cacheManager) {
        this.useCase = useCase;
        this.cacheManager = cacheManager;
    }

    @Transactional
    public MigrationBatch open(CentreId centerId, String libelle, String sourceSystem, LocalDate dateDebutReprise, String user) {
        return useCase.open(centerId, libelle, sourceSystem, dateDebutReprise, user);
    }

    @Transactional(readOnly = true)
    public PagedResult<MigrationBatch> list(CentreId centerId, int page, int size) {
        return useCase.list(centerId, page, size);
    }

    @Transactional(readOnly = true)
    public MigrationUseCase.BatchDetail get(CentreId centerId, UUID batchId) {
        return useCase.get(centerId, batchId);
    }

    @Transactional
    public EntityRun importEntity(CentreId centerId, UUID batchId, MigrationEntity entity, String fileName,
                                  ImportTable table, boolean dryRun, String user) {
        EntityRun run = useCase.importEntity(centerId, batchId, entity, fileName, table, dryRun, user);
        if (run.applied()) clearCachesAfterCommit();
        return run;
    }

    @Transactional
    public MigrationBatch close(CentreId centerId, UUID batchId) {
        return useCase.close(centerId, batchId);
    }

    @Transactional
    public MigrationBatch cancel(CentreId centerId, UUID batchId) {
        MigrationBatch batch = useCase.cancel(centerId, batchId);
        clearCachesAfterCommit();
        return batch;
    }

    @Transactional(readOnly = true)
    public Map<String, Map<String, String>> valueMappings(CentreId centerId) {
        return useCase.valueMappings(centerId);
    }

    @Transactional
    public void saveValueMapping(CentreId centerId, String column, String source, String target) {
        useCase.saveValueMapping(centerId, column, source, target);
    }

    @Transactional
    public void deleteValueMapping(CentreId centerId, String column, String source) {
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

