package com.hemodialyse.backend.contexts.stock.infrastructure.evenements;

import com.hemodialyse.backend.contexts.stock.application.inventaire.NotificationsDInventaire;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.InventaireModifie;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Publie {@link InventaireModifie} comme évènement Spring ; ceux qui l'écoutent (WebSocket du centre) sont dans la plateforme.
 */
public final class NotificationsDInventaireSpring implements NotificationsDInventaire {
    private final ApplicationEventPublisher evenements;

    public NotificationsDInventaireSpring(ApplicationEventPublisher evenements) {
        this.evenements = evenements;
    }

    @Override
    public void inventaireModifie(UUID centre, String statut, String reference, LocalDate date, String par) {
        evenements.publishEvent(new InventaireModifie(centre, statut, reference, date, par));
    }
}

