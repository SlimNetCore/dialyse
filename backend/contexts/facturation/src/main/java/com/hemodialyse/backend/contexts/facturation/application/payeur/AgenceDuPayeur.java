package com.hemodialyse.backend.contexts.facturation.application.payeur;

import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant : l'agence à laquelle un centre payeur du centre est rattaché, pour la figer sur une facture (instantané). Vide si le
 * centre payeur est inconnu ou d'un autre centre, ou s'il n'a pas d'agence.
 */
public interface AgenceDuPayeur {

    Optional<UUID> agenceDuCentrePayeur(UUID centre, UUID centrePayeur);
}
