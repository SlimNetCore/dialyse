package com.hemodialyse.backend.application.seance;

import com.hemodialyse.backend.domain.article.port.ArticleRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.SeanceRaccourciRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.SeanceRaccourciUseCase;
import com.hemodialyse.backend.domain.seance.service.SeanceRaccourciDomainService;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Façade transactionnelle et cache des raccourcis de consommables. Lus à chaque ouverture du poste infirmier et
 * rarement modifiés : mis en cache sous la clé {@code centerId} (isolation inter-centres), purgés à chaque écriture.
 */
@Service
@Transactional
public class SeanceRaccourciApplicationService implements SeanceRaccourciUseCase {

    public static final String CACHE = "seance.raccourcis";

    private final SeanceRaccourciDomainService delegate;

    public SeanceRaccourciApplicationService(SeanceRaccourciRepositoryPort repo, ArticleRepositoryPort articleRepo) {
        this.delegate = new SeanceRaccourciDomainService(repo, articleRepo);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CACHE, key = "#centerId.value()")
    public List<UUID> get(CenterId centerId) {
        return delegate.get(centerId);
    }

    @Override
    @CacheEvict(cacheNames = CACHE, key = "#centerId.value()")
    public List<UUID> replace(CenterId centerId, List<UUID> articleIds) {
        return delegate.replace(centerId, articleIds);
    }
}
