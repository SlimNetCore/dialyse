package com.hemodialyse.backend.contexts.seance.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * VO : forfait surchargé appliqué à une séance (RG-FAC-021, priorité surcharge).
 */
public record ForfaitApplique(UUID id, String code, String libelle, BigDecimal prixHt,
                              String auteur, OffsetDateTime instant) {

    public ForfaitApplique {
        if (id == null) throw new IllegalArgumentException("ForfaitId requis");
        if (code == null || code.isBlank()) throw new IllegalArgumentException("ForfaitCode requis");
        if (libelle == null || libelle.isBlank()) throw new IllegalArgumentException("ForfaitLibelle requis");
        if (auteur == null || auteur.isBlank()) throw new IllegalArgumentException("Auteur requis");
        if (instant == null) throw new IllegalArgumentException("Instant requis");
        prixHt = prixHt == null ? BigDecimal.ZERO : prixHt;
        code = code.trim();
        libelle = libelle.trim();
        auteur = auteur.trim();
    }

    public boolean estDefini() {
        return id != null;
    }
}