package com.hemodialyse.backend.infrastructure.persistence.adapter.referential;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;

/**
 * Invalide les caches de référentiels d'un centre après une écriture.
 * <p>
 * Les clés de ces caches commencent toutes par l'identifiant du centre (ex. {@code centerId} ou
 * {@code centerId:salle:…}) : seules les entrées du centre modifié sont retirées (isolation multi-centre).
 * L'éviction a lieu après le commit, pour qu'aucune lecture concurrente ne remette en cache l'état d'avant.
 */
@Component
public class ReferentialCacheEvictor {

    private final CacheManager cacheManager;

    public ReferentialCacheEvictor(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void evictAfterCommit(CenterId centerId, Collection<String> cacheNames) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictNow(centerId, cacheNames);
                }
            });
        } else {
            evictNow(centerId, cacheNames);
        }
    }

    void evictNow(CenterId centerId, Collection<String> cacheNames) {
        String prefix = centerId.value().toString();
        for (String name : cacheNames) {
            Cache cache = cacheManager.getCache(name);
            if (cache == null) continue;
            if (cache.getNativeCache() instanceof com.github.benmanes.caffeine.cache.Cache<?, ?> caffeine) {
                caffeine.asMap().keySet().removeIf(key -> String.valueOf(key).startsWith(prefix));
            } else {
                cache.clear();
            }
        }
    }
}

