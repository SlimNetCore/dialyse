package com.hemodialyse.backend.contexts.stock.application;

import java.util.List;
import java.util.UUID;

/**
 * Port sortant : notifications temps réel du Stock (mouvement modifié, verrous de recalcul) ; l'adaptateur publie un événement
 * consommé par la plateforme (WebSocket du centre, éviction du cache de valorisation).
 */
public interface NotificationsDuStock {

    /**
     * @param mouvement {@code ENTREE} ou {@code SORTIE}
     */
    void mouvementsModifies(UUID centre, String mouvement, String reference, int nombreArticles);

    /**
     * @param etat {@code STARTED}, {@code COMPLETED} ou {@code FAILED}
     */
    void verrousDeRecalculModifies(UUID centre, List<UUID> articles, String etat);
}

