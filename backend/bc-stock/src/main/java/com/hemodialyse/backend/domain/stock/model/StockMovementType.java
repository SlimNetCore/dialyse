package com.hemodialyse.backend.domain.stock.model;

public enum StockMovementType {
    ENTREE,
    SORTIE,
    AJUSTEMENT,
    /**
     * Stock de depart pose par la cloture d'un inventaire (quantite comptee, valorisee au PMP de l'inventaire).
     * Les mouvements anterieurs sont clotures : le recalcul repart de ces mouvements.
     */
    INVENTAIRE
}


