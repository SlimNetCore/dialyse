package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.domain.NiveauDePayeur;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.UUID;

/**
 * Port sortant : ce qui retient un payeur à la suppression - les agences d'une caisse, les centres payeurs d'une agence, les patients
 * d'un centre payeur. Les références sont comptées par identité de payeur ET BORNÉES AU CENTRE (RF4, constat 7 de la revue RF1-RF3d,
 * comme RF1c pour les transporteurs et RF2c pour les forfaits) : une ligne d'un autre centre ne retient jamais un payeur.
 */
public interface UsageDesPayeurs {

    long nombreDeReferences(CentreId centre, NiveauDePayeur niveau, UUID id);
}
