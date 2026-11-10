package com.hemodialyse.backend.contexts.pilotagedirection.application;

import java.util.Map;
import java.util.UUID;

/**
 * Port sortant : la caisse de chaque centre payeur d'un centre (propriété de Patient et couverture, RF3b, ADR 0127), pour regrouper les
 * patients par caisse. Toujours bornée au centre indiqué.
 */
public interface CaissesDesCentresPayeurs {

    /** La caisse de chaque centre payeur du centre ; un centre payeur sans caisse DANS CE CENTRE est absent de la table. */
    Map<UUID, CaisseDuPayeur> parCentrePayeur(UUID centre);

    /** Code et nom d'une caisse d'assurance. */
    record CaisseDuPayeur(String code, String nom) {
    }
}
