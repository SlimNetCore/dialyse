package com.hemodialyse.backend.contexts.facturation.application.pilotage;

import java.util.Map;
import java.util.UUID;

/**
 * Port sortant de la synthèse : la caisse d'assurance de chaque agence d'un centre (annuaire des payeurs, RF3c-J1). Bornée au centre à
 * chaque étage : une agence ou une caisse d'un autre centre est absente.
 */
@FunctionalInterface
public interface CaissesDesAgences {

    /** Code (tel qu'enregistré) et nom de la caisse de chaque agence du centre qui en a une. */
    Map<UUID, Caisse> parAgence(UUID centre);

    record Caisse(String code, String nom) {
    }
}
