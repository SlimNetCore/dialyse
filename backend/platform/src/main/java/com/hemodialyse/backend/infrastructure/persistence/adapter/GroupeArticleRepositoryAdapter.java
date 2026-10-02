package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.stock.model.GroupeArticle;
import com.hemodialyse.backend.domain.stock.port.GroupeArticleRepositoryPort;
import com.hemodialyse.backend.infrastructure.persistence.entity.GroupeArticleJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.GroupeArticleJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class GroupeArticleRepositoryAdapter implements GroupeArticleRepositoryPort {

    private final GroupeArticleJpaRepository jpa;

    public GroupeArticleRepositoryAdapter(GroupeArticleJpaRepository jpa) {
        this.jpa = jpa;
    }

    private static GroupeArticle toDomain(GroupeArticleJpaEntity e) {
        return GroupeArticle.reconstruct(e.getId(), e.getCenterId(), e.getNom(), e.getDescription(),
                e.getArticleIds(), e.getUpdatedAt());
    }

    @Override
    @Transactional
    public GroupeArticle save(GroupeArticle g) {
        jpa.save(new GroupeArticleJpaEntity(
                g.id(), g.centerId(), g.nom(), g.cle(), g.description(), g.articleIds(), g.updatedAt()));
        return g;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<GroupeArticle> findById(UUID centerId, UUID id) {
        return jpa.findByIdAndCenterId(id, centerId).map(GroupeArticleRepositoryAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResult<GroupeArticle> findPaged(UUID centerId, int page, int size) {
        Page<GroupeArticleJpaEntity> result = jpa.findByCenterId(centerId, PageRequest.of(page, size, Sort.by("nomCle")));
        return PagedResult.of(result.getContent().stream().map(GroupeArticleRepositoryAdapter::toDomain).toList(),
                result.getTotalElements(), page, size);
    }

    @Override
    public boolean existsByCle(UUID centerId, String cle, UUID excludeId) {
        return excludeId == null
                ? jpa.existsByCenterIdAndNomCle(centerId, cle)
                : jpa.existsByCenterIdAndNomCleAndIdNot(centerId, cle, excludeId);
    }

    @Override
    @Transactional
    public void delete(UUID centerId, UUID id) {
        jpa.findByIdAndCenterId(id, centerId).ifPresent(jpa::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupeArticle> findByCenters(Collection<UUID> centerIds) {
        if (centerIds == null || centerIds.isEmpty()) return List.of();
        return jpa.findByCenterIdIn(centerIds).stream().map(GroupeArticleRepositoryAdapter::toDomain).toList();
    }
}
