package com.hemodialyse.backend.domain.stock.port;

import java.util.List;
import java.util.UUID;

/**
 * Port Out — publishes real-time stock domain events to interested subscribers.
 * <p>
 * Keeps the transport concern (WebSocket/STOMP) out of the domain layer
 * (hexagonal architecture — AGENTS.md §3). The infrastructure adapter is
 * responsible for the actual delivery to {@code /topic/center/{centerId}/events}
 * (multi-center scoping — AGENTS.md §2).
 */
public interface StockEventPublisher {

    /**
     * Signals that a stock movement (entry/exit) changed for a center.
     *
     * @param centerId     the owning center
     * @param mouvement    movement kind (e.g. {@code "ENTREE"} / {@code "SORTIE"})
     * @param reference    the related document reference (BR/BS)
     * @param articleCount number of impacted articles
     */
    void stockMovementChanged(UUID centerId, String mouvement, String reference, int articleCount);

    /**
     * Signals that the set of articles locked by a PMP recalculation job changed.
     *
     * @param centerId   the owning center
     * @param articleIds impacted article ids
     * @param status     lock transition (e.g. {@code "STARTED"} / {@code "FINISHED"})
     */
    void recalcLocksChanged(UUID centerId, List<UUID> articleIds, String status);
}

