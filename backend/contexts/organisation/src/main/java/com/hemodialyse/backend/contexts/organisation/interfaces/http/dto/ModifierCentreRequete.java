package com.hemodialyse.backend.contexts.organisation.interfaces.http.dto;

import com.hemodialyse.backend.contexts.organisation.domain.Coordonnees;

/**
 * Modification des paramètres d'un centre (l'activation transite par les routes dédiées).
 */
public record ModifierCentreRequete(String code, String nom, Coordonnees coordonnees) {
}
