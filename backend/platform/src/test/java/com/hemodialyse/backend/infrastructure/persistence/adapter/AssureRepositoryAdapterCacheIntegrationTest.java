package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.assure.model.Assure;
import com.hemodialyse.backend.domain.assure.port.AssureRepositoryPort;
import com.hemodialyse.backend.infrastructure.persistence.repository.AssureJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class AssureRepositoryAdapterCacheIntegrationTest {

    @Autowired
    private AssureRepositoryPort assureRepositoryPort;

    @Autowired
    private AssureJpaRepository assureJpaRepository;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        assureJpaRepository.deleteAll();
        Objects.requireNonNull(cacheManager.getCache("patient.assure.byNumero")).clear();
    }

    @Test
    void findByNumeroAssurance_should_return_empty_twice_without_cache_error_and_refresh_after_save() {
        var firstResult = assureRepositoryPort.findByNumeroAssurance("ASSURE-ABSENT");
        var secondResult = assureRepositoryPort.findByNumeroAssurance("ASSURE-ABSENT");

        Assure assure = new Assure();
        assure.setNumeroAssurance("ASSURE-ABSENT");
        assure.setCenterId(UUID.randomUUID());
        assure.setNom("Fall");
        assure.setPrenom("Awa");
        assure.setCreatedAt(OffsetDateTime.now());
        assureRepositoryPort.save(assure);

        var afterSaveResult = assureRepositoryPort.findByNumeroAssurance("ASSURE-ABSENT");

        assertTrue(firstResult.isEmpty());
        assertTrue(secondResult.isEmpty());
        assertTrue(afterSaveResult.isPresent());
        assertEquals("ASSURE-ABSENT", afterSaveResult.get().getNumeroAssurance());
    }

    @Test
    void findByNumeroAssurance_should_cache_existing_assure_by_exact_numero() {
        Assure assure = new Assure();
        assure.setNumeroAssurance("ASSURE-001");
        assure.setCenterId(UUID.randomUUID());
        assure.setNom("Ba");
        assure.setPrenom("Moussa");
        assure.setCreatedAt(OffsetDateTime.now());
        assureRepositoryPort.save(assure);

        var firstLookup = assureRepositoryPort.findByNumeroAssurance("ASSURE-001");
        assureJpaRepository.deleteAll();
        var secondLookup = assureRepositoryPort.findByNumeroAssurance("ASSURE-001");

        assertTrue(firstLookup.isPresent());
        assertTrue(secondLookup.isPresent());
        assertEquals("ASSURE-001", secondLookup.get().getNumeroAssurance());
        Cache.ValueWrapper cachedValue = cacheByNumero().get("ASSURE-001");
        assertNotNull(cachedValue);
        assertNotNull(cachedValue.get());
    }

    private Cache cacheByNumero() {
        return Objects.requireNonNull(cacheManager.getCache("patient.assure.byNumero"));
    }
}




