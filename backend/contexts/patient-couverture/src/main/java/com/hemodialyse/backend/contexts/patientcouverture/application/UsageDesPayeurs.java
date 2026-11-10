package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.domain.NiveauDePayeur;

import java.util.UUID;

/**
 * Port sortant : ce qui retient un payeur à la suppression - les agences d'une caisse, les centres payeurs d'une agence, les patients
 * d'un centre payeur. Les références sont comptées par identité de payeur (comme avant la refonte).
 */
public interface UsageDesPayeurs {

    long nombreDeReferences(NiveauDePayeur niveau, UUID id);
}
