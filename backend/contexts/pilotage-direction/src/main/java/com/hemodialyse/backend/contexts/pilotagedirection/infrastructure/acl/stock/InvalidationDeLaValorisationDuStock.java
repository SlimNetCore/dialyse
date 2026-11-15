package com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.stock;

import com.hemodialyse.backend.contexts.stock.publishedlanguage.MouvementsDeStockModifies;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.VerrousDeRecalculModifies;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Vide le cache de la valorisation du stock ({@link StockDesGroupesParLeStock#CACHE}), que le Pilotage direction possède, dès que le
 * contexte Stock publie un changement de mouvements ({@link MouvementsDeStockModifies}) ou la fin d'un recalcul
 * ({@link VerrousDeRecalculModifies}). Écouteur du langage publié par Stock, après validation de la transaction (CLOT1, ADR 0203 :
 * l'invalidation n'est plus portée par le transport STOMP de la plateforme).
 */
public class InvalidationDeLaValorisationDuStock {

    private final ObjectProvider<CacheManager> caches;

    public InvalidationDeLaValorisationDuStock(ObjectProvider<CacheManager> caches) {
        this.caches = caches;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void mouvementsModifies(MouvementsDeStockModifies evenement) {
        vider();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void verrousDeRecalcul(VerrousDeRecalculModifies evenement) {
        if (!"STARTED".equals(evenement.etat())) {
            vider();
        }
    }

    private void vider() {
        CacheManager gestionnaire = caches.getIfAvailable();
        Cache valorisation = gestionnaire == null ? null : gestionnaire.getCache(StockDesGroupesParLeStock.CACHE);
        if (valorisation != null) {
            valorisation.clear();
        }
    }
}
