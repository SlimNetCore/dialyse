package com.hemodialyse.backend.infrastructure.websocket;

import com.hemodialyse.backend.application.direction.DirectionRealtimeService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Suit les directions connectées : le temps réel ne recalcule que les sociétés qui ont au moins un abonné. Un
 * abonnement n'atteint cet écouteur que s'il a passé le contrôle de {@link WebSocketAuthentication}.
 */
@Component
public class DirectionSubscriptionTracker {

    private final DirectionRealtimeService realtime;
    /**
     * « session/abonnement » → société.
     */
    private final Map<String, UUID> subscriptions = new ConcurrentHashMap<>();

    public DirectionSubscriptionTracker(DirectionRealtimeService realtime) {
        this.realtime = realtime;
    }

    private static String key(String sessionId, String subscriptionId) {
        return sessionId + "/" + subscriptionId;
    }

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(WebSocketAuthentication.SOCIETE_TOPIC_PREFIX)) {
            return;
        }
        try {
            UUID societeId = UUID.fromString(
                    destination.substring(WebSocketAuthentication.SOCIETE_TOPIC_PREFIX.length()).split("/", 2)[0]);
            if (subscriptions.putIfAbsent(key(accessor.getSessionId(), accessor.getSubscriptionId()), societeId) == null) {
                realtime.watcherAdded(societeId);
            }
        } catch (IllegalArgumentException ignored) {
            // identifiant de société illisible : l'abonnement a déjà été refusé en amont
        }
    }

    @EventListener
    public void onUnsubscribe(SessionUnsubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        release(key(accessor.getSessionId(), accessor.getSubscriptionId()));
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String prefix = event.getSessionId() + "/";
        subscriptions.keySet().stream().filter(k -> k.startsWith(prefix)).toList().forEach(this::release);
    }

    private void release(String key) {
        UUID societeId = subscriptions.remove(key);
        if (societeId != null) {
            realtime.watcherRemoved(societeId);
        }
    }
}
