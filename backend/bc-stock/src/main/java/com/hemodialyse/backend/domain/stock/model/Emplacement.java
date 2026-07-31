package com.hemodialyse.backend.domain.stock.model;

import java.util.UUID;

/**
 * Emplacement (storage location) referential for the stock module.
 */
public record Emplacement(
        UUID id,
        UUID centerId,
        String code,
        String libelle,
        boolean actif
) {
    public static Emplacement create(UUID centerId, String code, String libelle) {
        return new Emplacement(UUID.randomUUID(), centerId, code, libelle, true);
    }
}

