package com.hemodialyse.backend.contexts.organisation.interfaces.http.dto;

import java.util.UUID;

/**
 * Création d'un créneau (position) dans un centre.
 */
public record CreerCreneauRequete(UUID centreId, String code, String libelle) {
}
