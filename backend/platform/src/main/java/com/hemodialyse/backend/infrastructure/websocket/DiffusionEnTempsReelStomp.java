package com.hemodialyse.backend.infrastructure.websocket;

import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DiffusionEnTempsReel;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TempsReelDeLaDirection;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Transport temps réel (port {@link DiffusionEnTempsReel} du contexte Notifications, NOT1, ADR 0141) : le message STOMP du centre, puis
 * l'avertissement de la direction de la société. La plateforme ne fournit plus que ce transport (PL13d, ADR 0156) : les destinations et la
 * forme du message sont celles d'avant le contexte.
 */
@Component
public class DiffusionEnTempsReelStomp implements DiffusionEnTempsReel {

    private static final String CLE_DES_ROLES = "targetRoles";
    private static final String CLE_DU_DESTINATAIRE = "targetUserId";

    private final SimpMessagingTemplate messaging;
    private final TempsReelDeLaDirection directionRealtime;

    public DiffusionEnTempsReelStomp(SimpMessagingTemplate messaging, TempsReelDeLaDirection directionRealtime) {
        this.messaging = messaging;
        this.directionRealtime = directionRealtime;
    }

    @Override
    public void diffuser(UUID centerId, String eventType, Map<String, String> payload, UUID id, Instant maintenant) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", eventType);
        event.put("centerId", centerId.toString());
        // Alerte personnelle : le canal temps réel du centre ne porte que la cible ; le contenu (infirmier, date, salle, créneau)
        // se lit au journal, filtré par compte (revue P8, M2)
        event.put("payload", payload.containsKey(CLE_DU_DESTINATAIRE)
                ? Map.of(CLE_DES_ROLES, payload.getOrDefault(CLE_DES_ROLES, ""), CLE_DU_DESTINATAIRE, payload.get(CLE_DU_DESTINATAIRE))
                : payload);
        event.put("timestamp", maintenant.toString());
        if (id != null) event.put("id", id.toString());
        String destination = "/topic/center/" + centerId + "/events";
        messaging.convertAndSend(destination, (Object) event);
        // La direction de la société voit aussi ce changement, sans attendre le prochain balayage.
        directionRealtime.unCentreAChange(centerId);
    }
}
