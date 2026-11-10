package com.hemodialyse.backend.infrastructure.referential.admin.port;

import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialEntry;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialKind;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les délégués de l'administration générique, assemblés (RF1 : transporteurs, RF2 : forfaits) : chaque référentiel est confié au
 * premier délégué qui le prend ; l'administration générique n'en connaît qu'un seul.
 */
public class ReferentialAdminDelegates implements ReferentialAdminDelegate {

    private final List<ReferentialAdminDelegate> delegues;

    public ReferentialAdminDelegates(List<ReferentialAdminDelegate> delegues) {
        this.delegues = List.copyOf(delegues);
    }

    private ReferentialAdminDelegate de(ReferentialKind kind) {
        return delegues.stream().filter(d -> d.prend(kind)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Aucun délégué pour " + kind));
    }

    @Override
    public boolean prend(ReferentialKind kind) {
        return delegues.stream().anyMatch(d -> d.prend(kind));
    }

    @Override
    public PagedResult<ReferentialEntry> list(CenterId centerId, ReferentialKind kind, String search, int page, int size) {
        return de(kind).list(centerId, kind, search, page, size);
    }

    @Override
    public ReferentialEntry create(CenterId centerId, ReferentialKind kind, Map<String, String> values) {
        return de(kind).create(centerId, kind, values);
    }

    @Override
    public ReferentialEntry update(CenterId centerId, ReferentialKind kind, UUID id, Map<String, String> values) {
        return de(kind).update(centerId, kind, id, values);
    }

    @Override
    public void delete(CenterId centerId, ReferentialKind kind, UUID id) {
        de(kind).delete(centerId, kind, id);
    }

    @Override
    public Outcome importRows(CenterId centerId, ReferentialKind kind, List<Row> rows, boolean dryRun) {
        return de(kind).importRows(centerId, kind, rows, dryRun);
    }
}
