package com.hemodialyse.backend.domain.stock.model;

/**
 * Cycle de vie d'un inventaire de stock.
 */
public enum InventaireStatut {
    /**
     * Comptage en cours : aucun mouvement de stock n'est permis dans le centre.
     */
    EN_COURS,
    /**
     * Inventaire validé : les quantités comptées sont le stock de départ, les mouvements antérieurs sont clôturés.
     */
    CLOTURE,
    /**
     * Inventaire abandonné : aucun effet sur le stock.
     */
    ANNULE
}

