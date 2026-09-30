package com.hemodialyse.backend.domain.referential.admin;

import com.hemodialyse.backend.domain.referential.admin.model.ReferentialEntry;
import com.hemodialyse.backend.domain.referential.admin.model.ReferentialKind;
import com.hemodialyse.backend.domain.referential.admin.port.ReferentialAdminRepositoryPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Dépôt en mémoire, cloisonné par centre, pour tester le domaine sans base de données.
 */
class InMemoryReferentialAdminRepository implements ReferentialAdminRepositoryPort {

    final List<Stored> rows = new ArrayList<>();
    final Map<UUID, Long> usages = new HashMap<>();
    int writes;

    UUID seed(CenterId centerId, ReferentialKind kind, Map<String, String> values) {
        UUID id = UUID.randomUUID();
        rows.add(new Stored(centerId, kind, id, new LinkedHashMap<>(values)));
        return id;
    }

    List<Stored> of(CenterId centerId, ReferentialKind kind) {
        return rows.stream().filter(r -> r.centerId().equals(centerId) && r.kind() == kind).toList();
    }

    @Override
    public PagedResult<ReferentialEntry> findPaged(CenterId centerId, ReferentialKind kind, String search, int page, int size) {
        List<ReferentialEntry> all = findAllForMatching(centerId, kind);
        return PagedResult.of(all.stream().skip((long) page * size).limit(size).toList(), all.size(), page, size);
    }

    @Override
    public Optional<ReferentialEntry> findById(CenterId centerId, ReferentialKind kind, UUID id) {
        return of(centerId, kind).stream().filter(r -> r.id().equals(id)).findFirst()
                .map(r -> new ReferentialEntry(r.id(), r.values(), Map.of()));
    }

    @Override
    public List<ReferentialEntry> findAllForMatching(CenterId centerId, ReferentialKind kind) {
        return of(centerId, kind).stream().map(r -> new ReferentialEntry(r.id(), r.values(), Map.of())).toList();
    }

    @Override
    public UUID insert(CenterId centerId, ReferentialKind kind, Map<String, String> values) {
        writes++;
        return seed(centerId, kind, values);
    }

    @Override
    public void update(CenterId centerId, ReferentialKind kind, UUID id, Map<String, String> values) {
        writes++;
        rows.replaceAll(r -> r.id().equals(id) && r.centerId().equals(centerId)
                ? new Stored(centerId, kind, id, new LinkedHashMap<>(values)) : r);
    }

    @Override
    public void delete(CenterId centerId, ReferentialKind kind, UUID id) {
        writes++;
        rows.removeIf(r -> r.id().equals(id) && r.centerId().equals(centerId));
    }

    @Override
    public long countUsages(CenterId centerId, ReferentialKind kind, UUID id) {
        return usages.getOrDefault(id, 0L);
    }

    record Stored(CenterId centerId, ReferentialKind kind, UUID id, Map<String, String> values) {
    }
}

