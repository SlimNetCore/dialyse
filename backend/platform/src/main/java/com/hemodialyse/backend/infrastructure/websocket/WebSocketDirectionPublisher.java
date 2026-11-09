package com.hemodialyse.backend.infrastructure.websocket;

import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.DiffusionDuTableauDeBordFournie;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Diffuse les changements du tableau de bord sur le canal STOMP de la société ({@code /topic/societe/{id}/dashboard}). Adaptateur de sortie
 * du port fourni au Pilotage direction (PD3, ADR 0104) : la détection vit dans le contexte, le transport reste ici. L'abonnement à ce canal
 * est réservé à la direction de la société ({@link WebSocketAuthentication}).
 */
@Component
public class WebSocketDirectionPublisher implements DiffusionDuTableauDeBordFournie {

    private final SimpMessagingTemplate messaging;

    public WebSocketDirectionPublisher(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @Override
    public void publish(UUID societeId, TableauDeBordModifie event) {
        messaging.convertAndSend("/topic/societe/" + societeId + "/dashboard", event);
    }
}
