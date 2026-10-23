package com.hemodialyse.backend.domain.stock.model;

public enum StockMovementType {
    ENTREE,
    SORTIE,
    AJUSTEMENT,
    /**
     * Stock de depart pose par la cloture d'un inventaire (quantite comptee, valorisee au PMP de l'inventaire).
     * Les mouvements anterieurs sont clotures : le recalcul repart de ces mouvements.
     */
    INVENTAIRE,
    /**
     * Retour en stock d'un surplus sorti pour des seances (rapprochement, S6b) : traite comme une entree au prix de sortie.
     */
    RETOUR
}


