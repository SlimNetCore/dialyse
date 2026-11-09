package com.hemodialyse.backend.infrastructure.websocket;

import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TempsReelDeLaDirection;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Infrastructure adapter — publishes stock domain events over STOMP/WebSocket.
 * <p>
 * Called by {@link StockEvenementsListener}, which relays the published events of the Stock context, so the domain stays
 * free of any messaging dependency (hexagonal architecture — AGENTS.md §3).
 * Events are scoped per center (AGENTS.md §2): {@code /topic/center/{centerId}/events}.
 */
@Component
public class WebSocketStockEventPublisher {

    private final SimpMessagingTemplate messaging;
    private final TempsReelDeLaDirection directionRealtime;

    public WebSocketStockEventPublisher(SimpMessagingTemplate messaging, TempsReelDeLaDirection directionRealtime) {
        this.messaging = messaging;
        this.directionRealtime = directionRealtime;
    }

    public void stockMovementChanged(UUID centerId, String mouvement, String reference, int articleCount) {
        Map<String, String> payload = new HashMap<>();
        payload.put("mouvement", mouvement);
        payload.put("reference", reference != null ? reference : "");
        payload.put("articles", Integer.toString(articleCount));
        send(centerId, "STOCK_MOVEMENT_CHANGED", payload);
    }

    public void recalcLocksChanged(UUID centerId, List<UUID> articleIds, String status) {
        Map<String, String> payload = new HashMap<>();
        payload.put("status", status);
        payload.put("articleIds", articleIds.stream().map(UUID::toString).reduce((a, b) -> a + "," + b).orElse(""));
        payload.put("count", Integer.toString(articleIds.size()));
        send(centerId, "STOCK_RECALC_LOCKS_CHANGED", payload);
    }

    private void send(UUID centerId, String type, Map<String, String> payload) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", type);
        event.put("centerId", centerId.toString());
        event.put("payload", payload);
        event.put("timestamp", Instant.now().toString());
        messaging.convertAndSend("/topic/center/" + centerId + "/events", (Object) event);
        directionRealtime.unCentreAChange(centerId);
    }
}

