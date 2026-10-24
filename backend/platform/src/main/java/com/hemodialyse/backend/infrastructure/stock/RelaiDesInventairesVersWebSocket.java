package com.hemodialyse.backend.infrastructure.stock;

import com.hemodialyse.backend.contexts.stock.publishedlanguage.InventaireModifie;
import com.hemodialyse.backend.infrastructure.websocket.WebSocketInventaireEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Relaie « un inventaire a changé d'état » du contexte Stock vers le WebSocket du centre (bandeau « mouvements gelés », cloche de
 * notifications), comme le faisait le service d'inventaire historique.
 */
@Component
public class RelaiDesInventairesVersWebSocket {
    private final WebSocketInventaireEventPublisher publication;

    public RelaiDesInventairesVersWebSocket(WebSocketInventaireEventPublisher publication) {
        this.publication = publication;
    }

    @EventListener
    void inventaireModifie(InventaireModifie evenement) {
        publication.inventaireChanged(evenement.centre(), evenement.statut(), evenement.reference(), evenement.date(), evenement.par());
    }
}


