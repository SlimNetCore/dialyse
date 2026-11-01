package com.hemodialyse.backend.contexts.organisation.interfaces.http.dto;

import com.hemodialyse.backend.contexts.organisation.domain.Coordonnees;

import java.util.UUID;

/**
 * Création d'un centre rattaché à une société existante.
 */
public record CreerCentreRequete(UUID societeId, String code, String nom, Coordonnees coordonnees,
                                 String fuseauHoraire) {
}
