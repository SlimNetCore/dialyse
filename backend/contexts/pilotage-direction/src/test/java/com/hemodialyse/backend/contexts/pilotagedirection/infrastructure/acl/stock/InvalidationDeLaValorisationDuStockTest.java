package com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.stock;

import com.hemodialyse.backend.contexts.stock.publishedlanguage.VerrousDeRecalculModifies;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** L'invalidation du cache de valorisation suit les événements publiés par Stock (un recalcul qui démarre n'invalide pas). */
class InvalidationDeLaValorisationDuStockTest {

    private Cache cache;
    private InvalidationDeLaValorisationDuStock invalidation;

    @BeforeEach
    void preparer() {
        CacheManager gestionnaire = new ConcurrentMapCacheManager(StockDesGroupesParLeStock.CACHE);
        cache = gestionnaire.getCache(StockDesGroupesParLeStock.CACHE);
        cache.put("cle", "valeur");
        StaticListableBeanFactory usine = new StaticListableBeanFactory();
        usine.addBean("cacheManager", gestionnaire);
        invalidation = new InvalidationDeLaValorisationDuStock(usine.getBeanProvider(CacheManager.class));
    }

    @Test
    void un_recalcul_termine_vide_le_cache_de_valorisation() {
        invalidation.verrousDeRecalcul(new VerrousDeRecalculModifies(UUID.randomUUID(), List.of(), "FINISHED"));

        assertThat(cache.get("cle")).isNull();
    }

    @Test
    void un_recalcul_qui_demarre_laisse_le_cache_intact() {
        invalidation.verrousDeRecalcul(new VerrousDeRecalculModifies(UUID.randomUUID(), List.of(), "STARTED"));

        assertThat(cache.get("cle")).isNotNull();
    }
}
