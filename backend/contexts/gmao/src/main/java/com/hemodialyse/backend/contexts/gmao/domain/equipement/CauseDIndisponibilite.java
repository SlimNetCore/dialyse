package com.hemodialyse.backend.contexts.gmao.domain.equipement;

/** Ce qui rend un équipement indisponible : une décision de l'administrateur ou l'état constaté par une intervention. */
public enum CauseDIndisponibilite {
    MISE_HORS_SERVICE,
    INTERVENTION
}
