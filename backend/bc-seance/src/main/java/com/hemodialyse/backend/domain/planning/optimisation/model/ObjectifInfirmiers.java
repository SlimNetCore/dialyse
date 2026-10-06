package com.hemodialyse.backend.domain.planning.optimisation.model;

/**
 * Arbitrage de la planification des infirmiers : {@code EQUITE} répartit les vacations le plus également possible ;
 * {@code ECONOMIE} mobilise le moins d'infirmiers possible (dans la limite des vacations hebdomadaires autorisées).
 */
public enum ObjectifInfirmiers {
    EQUITE,
    ECONOMIE
}
