package com.hemodialyse.backend.application.referential;

import com.hemodialyse.backend.domain.referential.admin.model.ImportReport;
import com.hemodialyse.backend.domain.referential.admin.model.ImportTable;
import com.hemodialyse.backend.domain.referential.admin.model.ReferentialEntry;
import com.hemodialyse.backend.domain.referential.admin.model.ReferentialKind;
import com.hemodialyse.backend.domain.referential.admin.port.ReferentialAdminUseCase;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Orchestration transactionnelle de l'administration des référentiels : un import est « tout ou rien »,
 * et les caches ne sont invalidés qu'après le commit (voir ReferentialCacheEvictor).
 */
@Service
public class ReferentialAdminApplicationService {

    private final ReferentialAdminUseCase useCase;

    public ReferentialAdminApplicationService(ReferentialAdminUseCase useCase) {
        this.useCase = useCase;
    }

    @Transactional(readOnly = true)
    public PagedResult<ReferentialEntry> list(CenterId centerId, ReferentialKind kind, String search, int page, int size) {
        return useCase.list(centerId, kind, search, page, size);
    }

    @Transactional
    public ReferentialEntry create(CenterId centerId, ReferentialKind kind, Map<String, String> values) {
        return useCase.create(centerId, kind, values);
    }

    @Transactional
    public ReferentialEntry update(CenterId centerId, ReferentialKind kind, UUID id, Map<String, String> values) {
        return useCase.update(centerId, kind, id, values);
    }

    @Transactional
    public void delete(CenterId centerId, ReferentialKind kind, UUID id) {
        useCase.delete(centerId, kind, id);
    }

    @Transactional
    public ImportReport importEntries(CenterId centerId, ReferentialKind kind, ImportTable table, boolean dryRun) {
        return useCase.importEntries(centerId, kind, table, dryRun);
    }
}

