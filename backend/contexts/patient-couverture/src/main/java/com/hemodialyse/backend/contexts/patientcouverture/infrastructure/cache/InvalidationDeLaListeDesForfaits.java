package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.cache;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.function.Consumer;

/**
 * Invalide la liste en cache des forfaits d'un centre ({@code ref.forfaits}, clé = identifiant du centre) APRÈS le commit de
 * l'écriture : aucune lecture concurrente ne remet en cache l'état d'avant. Hors transaction : tout de suite.
 */
public class InvalidationDeLaListeDesForfaits implements Consumer<CentreId> {

    static final String LISTE_DES_FORFAITS = "ref.forfaits";

    private final CacheManager caches;

    public InvalidationDeLaListeDesForfaits(CacheManager caches) {
        this.caches = caches;
    }

    @Override
    public void accept(CentreId centre) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalider(centre);
                }
            });
        } else {
            invalider(centre);
        }
    }

    private void invalider(CentreId centre) {
        Cache liste = caches.getCache(LISTE_DES_FORFAITS);
        if (liste != null) liste.evict(centre.valeur().toString());
    }
}
