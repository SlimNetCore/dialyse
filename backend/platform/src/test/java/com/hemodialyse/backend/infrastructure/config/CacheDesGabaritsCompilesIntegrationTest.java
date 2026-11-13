package com.hemodialyse.backend.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DOC3 (ADR 0163) : le cache des gabarits compilés, utilisé par {@code contexts/documents} ({@code CustomTemplateCompiler.CACHE_NAME}), reste
 * déclaré par le gestionnaire de caches commun de la plateforme, 12 h d'expiration. Sans cette déclaration le rendu d'un gabarit personnalisé
 * échouerait (cache inconnu) : ce garde en fige le nom et la durée.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class CacheDesGabaritsCompilesIntegrationTest {

    @Autowired
    private CacheManager cacheManager;

    @Test
    void the_compiled_template_cache_is_declared_with_a_twelve_hour_expiry() {
        var cache = cacheManager.getCache("reporting.customTemplates");

        assertNotNull(cache);
        var natif = ((CaffeineCache) cache).getNativeCache();
        assertTrue(natif.policy().expireAfterWrite().isPresent());
        assertEquals(Duration.ofHours(12), natif.policy().expireAfterWrite().get().getExpiresAfter());
    }
}
