package com.hemodialyse.backend.infrastructure.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    @Value("${app.cache.ttl.referentials:PT6H}")
    private Duration referentialTtl;

    @Value("${app.cache.ttl.patient-detail:PT15M}")
    private Duration patientDetailTtl;

    @Value("${app.cache.ttl.patient-list:PT3M}")
    private Duration patientListTtl;

    @Value("${app.cache.ttl.patient-count:PT3M}")
    private Duration patientCountTtl;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager() {
            @Override
            protected org.springframework.cache.Cache adaptCaffeineCache(String name,
                                                                         com.github.benmanes.caffeine.cache.Cache<Object, Object> cache) {
                if ("patient.assure.byNumero".equals(name)) {
                    return new CaffeineCache(name, cache, true);
                }
                return super.adaptCaffeineCache(name, cache);
            }
        };
        manager.setAllowNullValues(false);

        // Referential caches
        register(manager, "ref.centresPayeurs", referentialTtl);
        register(manager, "ref.agences", referentialTtl);
        register(manager, "ref.caisses", referentialTtl);
        register(manager, "ref.medecins", referentialTtl);
        register(manager, "ref.salles", referentialTtl);
        register(manager, "ref.positions", referentialTtl);
        register(manager, "ref.transporteurs", referentialTtl);
        register(manager, "ref.categoriesTransport", referentialTtl);
        register(manager, "ref.forfaits", referentialTtl);
        register(manager, "ref.centresPayeursDetails", referentialTtl);

        // Patient caches
        register(manager, "patient.byId", patientDetailTtl);
        register(manager, "patient.byNumeroAssurance", patientDetailTtl);
        register(manager, "patient.byCenter", patientListTtl);
        register(manager, "patient.countByCenter", patientCountTtl);
        register(manager, "patient.assure.byNumero", patientDetailTtl);
        register(manager, "patient.assure.searchByCenter", patientListTtl);
        register(manager, "patient.assignment.primary", patientDetailTtl);
        register(manager, "patient.assignment.history", patientListTtl);
        register(manager, "patient.attestation.byPatient", patientDetailTtl);
        register(manager, "patient.attestation.existsValidAt", patientDetailTtl);
        register(manager, "patient.pec.byId", patientDetailTtl);
        register(manager, "patient.pec.byPatient", patientDetailTtl);
        register(manager, "patient.pec.byCenter", patientListTtl);

        return manager;
    }

    private void register(CaffeineCacheManager manager, String cacheName, Duration ttl) {
        manager.registerCustomCache(cacheName,
                Caffeine.newBuilder()
                        .expireAfterWrite(ttl)
                        .maximumSize(10_000)
                        .build());
    }
}



