package com.hemodialyse.backend.infrastructure.websocket;

import com.hemodialyse.backend.application.direction.DirectionRealtimeService;
import com.hemodialyse.backend.domain.stock.port.InventaireEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Notifie l'ouverture, la clôture ou l'annulation d'un inventaire aux utilisateurs du module stock
 * ({@code targetRoles} : la cloche de notifications n'affiche l'événement qu'à ces rôles), sur le topic du centre.
 * L'envoi a lieu après le commit : une clôture qui échoue n'est jamais annoncée.
 */
@Component
public class WebSocketInventaireEventPublisher implements InventaireEventPublisher {

    /**
     * Rôles ayant accès au module stock.
     */
    static final String STOCK_ROLES = "ADMIN,PHARMACIEN,INFIRMIER";

    private final SimpMessagingTemplate messaging;
    private final DirectionRealtimeService directionRealtime;

    public WebSocketInventaireEventPublisher(SimpMessagingTemplate messaging, DirectionRealtimeService directionRealtime) {
        this.messaging = messaging;
        this.directionRealtime = directionRealtime;
    }

    @Override
    public void inventaireChanged(UUID centerId, String statut, String reference, LocalDate dateInventaire, String by) {
        Map<String, String> payload = new HashMap<>();
        payload.put("statut", statut);
        payload.put("reference", reference);
        payload.put("dateInventaire", dateInventaire != null ? dateInventaire.toString() : "");
        payload.put("par", by != null ? by : "");
        payload.put("targetRoles", STOCK_ROLES);

        Map<String, Object> event = new HashMap<>();
        event.put("type", "STOCK_INVENTORY_CHANGED");
        event.put("centerId", centerId.toString());
        event.put("payload", payload);
        event.put("timestamp", Instant.now().toString());

        Runnable send = () -> {
            messaging.convertAndSend("/topic/center/" + centerId + "/events", (Object) event);
            directionRealtime.markDirtyForCentre(centerId);
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }
}

