package com.hemodialyse.backend.infrastructure.websocket;

import com.hemodialyse.backend.contexts.stock.publishedlanguage.MouvementsDeStockModifies;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.VerrousDeRecalculModifies;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Relaie les événements du contexte Stock (slice S5) au temps réel du centre (STOMP) : après la validation de la transaction, les écrans
 * du centre se rafraîchissent. Transport pur (ADR 0203) : l'invalidation du cache de valorisation, propriété du Pilotage direction, est
 * portée par {@code InvalidationDeLaValorisationDuStock} dans ce contexte.
 */
@Component
public class StockEvenementsListener {

    private final WebSocketStockEventPublisher publisher;

    public StockEvenementsListener(WebSocketStockEventPublisher publisher) {
        this.publisher = publisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void mouvementsModifies(MouvementsDeStockModifies evenement) {
        publisher.stockMovementChanged(evenement.centre(), evenement.mouvement(), evenement.reference(), evenement.nombreArticles());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void verrousDeRecalcul(VerrousDeRecalculModifies evenement) {
        publisher.recalcLocksChanged(evenement.centre(), evenement.articles(), evenement.etat());
    }
}
