package com.hemodialyse.backend.contexts.organisation.infrastructure.persistence;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Invalide les caches {@code ref.*} d'un centre APRÈS le commit de l'écriture (hors transaction : tout de suite) : une lecture
 * concurrente ne peut plus remettre en cache l'état d'avant, ce que fait un {@code @CacheEvict} exécuté avant le commit.
 */
public final class EvictionApresCommit {

    /** Aucun cache (contrats JDBC sans Spring). */
    public static final EvictionApresCommit AUCUNE = new EvictionApresCommit(null);

    private final CacheManager caches;

    public EvictionApresCommit(CacheManager caches) {
        this.caches = caches;
    }

    /** Retire l'entrée {@code cle} du cache {@code nom} ; {@code cle} nulle : vide tout le cache. */
    public void retirer(String nom, String cle) {
        if (caches == null) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    maintenant(nom, cle);
                }
            });
        } else {
            maintenant(nom, cle);
        }
    }

    private void maintenant(String nom, String cle) {
        Cache cache = caches.getCache(nom);
        if (cache == null) return;
        if (cle == null) cache.clear();
        else cache.evict(cle);
    }
}
