package com.hemodialyse.backend.domain.stock.port;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Port Out — notifie les utilisateurs du module stock des changements d'état d'un inventaire (ouverture = gel
 * des mouvements, clôture, annulation). L'adaptateur cible les rôles ayant accès au stock.
 */
public interface InventaireEventPublisher {

    /**
     * @param statut EN_COURS (ouvert) / CLOTURE / ANNULE
     */
    void inventaireChanged(UUID centerId, String statut, String reference, LocalDate dateInventaire, String by);
}

