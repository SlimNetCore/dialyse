package com.hemodialyse.backend.contexts.stock.infrastructure.evenements;

import com.hemodialyse.backend.contexts.stock.application.NotificationsDuStock;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.MouvementsDeStockModifies;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.VerrousDeRecalculModifies;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.UUID;

/**
 * Publie les notifications du Stock comme événements Spring du langage publié ; la plateforme les consomme après la validation
 * de la transaction.
 */
public final class NotificationsDuStockSpring implements NotificationsDuStock {

    private final ApplicationEventPublisher evenements;

    public NotificationsDuStockSpring(ApplicationEventPublisher evenements) {
        this.evenements = evenements;
    }

    @Override
    public void mouvementsModifies(UUID centre, String mouvement, String reference, int nombreArticles) {
        evenements.publishEvent(new MouvementsDeStockModifies(centre, mouvement, reference, nombreArticles));
    }

    @Override
    public void verrousDeRecalculModifies(UUID centre, List<UUID> articles, String etat) {
        evenements.publishEvent(new VerrousDeRecalculModifies(centre, List.copyOf(articles), etat));
    }
}

