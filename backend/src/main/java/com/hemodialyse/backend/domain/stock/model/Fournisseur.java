package com.hemodialyse.backend.domain.stock.model;

import java.util.UUID;

/**
 * Fournisseur (supplier) referential for the stock module.
 */
public record Fournisseur(
        UUID id,
        UUID centerId,
        String code,
        String raisonSociale,
        String contact,
        String telephone,
        String email,
        boolean actif
) {
    public static Fournisseur create(UUID centerId, String code, String raisonSociale,
                                     String contact, String telephone, String email) {
        return new Fournisseur(UUID.randomUUID(), centerId, code, raisonSociale,
                contact, telephone, email, true);
    }
}

