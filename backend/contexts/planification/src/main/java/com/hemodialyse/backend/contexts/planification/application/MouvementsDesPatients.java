package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.PlaceTenue.PlaceLiberee;

/**
 * Port sortant vers Patient et couverture : le mouvement {@code PLACE_LIBEREE} de l'historique du patient. Écrit dans la MÊME
 * transaction que la libération (l'événement {@code PlaceLiberee} v1 reste différé tant que Patient et couverture est legacy,
 * ADR 0052).
 */
public interface MouvementsDesPatients {

    /**
     * Le dernier mouvement du patient est-il déjà cette libération (même état, même place quittée) ? Alors il ne s'en écrit pas une
     * seconde (fiche d'un sorti renvoyée avec sa place).
     */
    boolean dejaTrace(PlaceLiberee liberee);

    void tracer(PlaceLiberee liberee);
}
