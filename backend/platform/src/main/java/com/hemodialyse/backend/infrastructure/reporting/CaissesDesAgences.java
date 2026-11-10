package com.hemodialyse.backend.infrastructure.reporting;

import java.util.Map;
import java.util.UUID;

/**
 * Port des impressions : le code de la caisse d'assurance de chaque agence d'un centre (propriété de Patient et couverture, RF3b, ADR 0127).
 * Bornée au centre à chaque étage : une agence ou une caisse d'un autre centre est absente de la table.
 */
@FunctionalInterface
public interface CaissesDesAgences {

    /** Code de caisse (tel qu'enregistré, éventuellement avec des espaces) par identifiant d'agence du centre. */
    Map<UUID, String> codesParAgence(UUID centre);
}
