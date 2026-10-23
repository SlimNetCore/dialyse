package com.hemodialyse.backend.infrastructure.websocket;

import com.hemodialyse.backend.application.stock.ValorisationArticlesService;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.MouvementsDeStockModifies;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.VerrousDeRecalculModifies;
import com.hemodialyse.backend.domain.stock.port.StockEventPublisher;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Relaie les événements du contexte Stock (slice S5) au temps réel du centre (STOMP) et au cache de valorisation : après la
 * validation de la transaction, les écrans du centre se rafraîchissent et la valorisation historique (lue par la direction) est
 * recalculée, comme quand le legacy écrivait les mouvements.
 */
@Component
public class StockEvenementsListener {

    private final StockEventPublisher publisher;
    private final CacheManager caches;

    public StockEvenementsListener(StockEventPublisher publisher, CacheManager caches) {
        this.publisher = publisher;
        this.caches = caches;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void mouvementsModifies(MouvementsDeStockModifies evenement) {
        Cache valorisation = caches.getCache(ValorisationArticlesService.CACHE);
        if (valorisation != null) {
            valorisation.clear();
        }
        publisher.stockMovementChanged(evenement.centre(), evenement.mouvement(), evenement.reference(), evenement.nombreArticles());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void verrousDeRecalcul(VerrousDeRecalculModifies evenement) {
        Cache valorisation = caches.getCache(ValorisationArticlesService.CACHE);
        if (valorisation != null && !"STARTED".equals(evenement.etat())) {
            valorisation.clear();
        }
        publisher.recalcLocksChanged(evenement.centre(), evenement.articles(), evenement.etat());
    }
}

