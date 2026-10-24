package com.hemodialyse.backend.contexts.stock.application.inventaire;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Port sortant : notification temps réel d'un changement d'inventaire (ouverture, clôture, annulation) ; l'adaptateur publie un
 * évènement consommé par la plateforme (WebSocket du centre).
 */
public interface NotificationsDInventaire {

    void inventaireModifie(UUID centre, String statut, String reference, LocalDate date, String par);
}

