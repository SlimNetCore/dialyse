package com.hemodialyse.backend.domain.seance.service;

import com.hemodialyse.backend.domain.article.model.Article;
import com.hemodialyse.backend.domain.article.port.ArticleRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.SeanceRaccourciRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.SeanceRaccourciUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Règles des raccourcis de consommables (domaine pur : aucune dépendance Spring/JPA).
 */
public class SeanceRaccourciDomainService implements SeanceRaccourciUseCase {

    private final SeanceRaccourciRepositoryPort repo;
    private final ArticleRepositoryPort articleRepo;

    public SeanceRaccourciDomainService(SeanceRaccourciRepositoryPort repo, ArticleRepositoryPort articleRepo) {
        this.repo = repo;
        this.articleRepo = articleRepo;
    }

    @Override
    public List<UUID> get(CenterId centerId) {
        return repo.findArticleIds(centerId);
    }

    @Override
    public List<UUID> replace(CenterId centerId, List<UUID> articleIds) {
        List<UUID> ids = articleIds == null ? List.of() : articleIds;
        if (ids.size() > MAX) {
            throw new IllegalArgumentException("Au plus " + MAX + " raccourcis de consommables");
        }
        Set<UUID> seen = new HashSet<>();
        for (UUID id : ids) {
            if (id == null || !seen.add(id)) {
                throw new IllegalArgumentException("Article en double ou invalide dans les raccourcis");
            }
            Article article = articleRepo.findById(id, centerId)
                    .orElseThrow(() -> new IllegalArgumentException("Article introuvable: " + id));
            if (!article.isActive()) {
                throw new IllegalStateException("Article inactif: " + article.getCode());
            }
        }
        repo.replace(centerId, ids);
        return List.copyOf(ids);
    }
}
