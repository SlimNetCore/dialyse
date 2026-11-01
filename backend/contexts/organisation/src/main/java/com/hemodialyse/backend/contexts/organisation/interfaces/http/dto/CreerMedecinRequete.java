package com.hemodialyse.backend.contexts.organisation.interfaces.http.dto;

import java.util.UUID;

/**
 * Référencement d'un médecin dans un centre.
 */
public record CreerMedecinRequete(UUID centreId, String nom, String prenom, String specialite) {
}
