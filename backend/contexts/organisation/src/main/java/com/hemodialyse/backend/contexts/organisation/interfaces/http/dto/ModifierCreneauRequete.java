package com.hemodialyse.backend.contexts.organisation.interfaces.http.dto;

import java.util.UUID;

/**
 * Modification d'un créneau (position) d'un centre.
 */
public record ModifierCreneauRequete(UUID centreId, String code, String libelle) {
}
