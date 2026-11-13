package com.hemodialyse.backend.application.referential;

import com.hemodialyse.backend.infrastructure.referential.admin.model.ImportReport;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ImportTable;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialEntry;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialKind;
import com.hemodialyse.backend.infrastructure.referential.admin.port.ReferentialAdminUseCase;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Orchestration transactionnelle de l'administration des référentiels : un import est « tout ou rien » ; l'invalidation des caches
 * est portée par les contextes propriétaires.
 */
@Service
public class ReferentialAdminApplicationService {

    private final ReferentialAdminUseCase useCase;

    public ReferentialAdminApplicationService(ReferentialAdminUseCase useCase) {
        this.useCase = useCase;
    }

    @Transactional(readOnly = true)
    public PagedResult<ReferentialEntry> list(CentreId centerId, ReferentialKind kind, String search, int page, int size) {
        return useCase.list(centerId, kind, search, page, size);
    }

    @Transactional
    public ReferentialEntry create(CentreId centerId, ReferentialKind kind, Map<String, String> values) {
        return useCase.create(centerId, kind, values);
    }

    @Transactional
    public ReferentialEntry update(CentreId centerId, ReferentialKind kind, UUID id, Map<String, String> values) {
        return useCase.update(centerId, kind, id, values);
    }

    @Transactional
    public void delete(CentreId centerId, ReferentialKind kind, UUID id) {
        useCase.delete(centerId, kind, id);
    }

    @Transactional
    public ImportReport importEntries(CentreId centerId, ReferentialKind kind, ImportTable table, boolean dryRun) {
        return useCase.importEntries(centerId, kind, table, dryRun);
    }
}

