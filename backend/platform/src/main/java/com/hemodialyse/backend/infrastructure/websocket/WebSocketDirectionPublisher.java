package com.hemodialyse.backend.infrastructure.websocket;

import com.hemodialyse.backend.application.direction.DirectionRealtimePort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Diffuse les changements du tableau de bord sur le canal STOMP de la société ({@code /topic/societe/{id}/dashboard}).
 * L'abonnement à ce canal est réservé à la direction de la société ({@link WebSocketAuthentication}).
 */
@Component
public class WebSocketDirectionPublisher implements DirectionRealtimePort {

    private final SimpMessagingTemplate messaging;

    public WebSocketDirectionPublisher(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @Override
    public void publish(UUID societeId, DashboardChanged event) {
        messaging.convertAndSend("/topic/societe/" + societeId + "/dashboard", event);
    }
}
