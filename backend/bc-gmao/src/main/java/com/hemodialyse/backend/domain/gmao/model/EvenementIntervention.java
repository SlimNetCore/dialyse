package com.hemodialyse.backend.domain.gmao.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entrée de la ligne de temps d'une intervention : qui a fait quoi, et quand (instant UTC).
 * {@code detail} porte une précision (ex. le motif d'une rectification).
 */
public record EvenementIntervention(Type type, OffsetDateTime at, UUID par, String detail) {

    public EvenementIntervention(Type type, OffsetDateTime at, UUID par) {
        this(type, at, par, null);
    }

    public enum Type {
        CREEE,
        DEMARREE,
        TERMINEE,
        ANNULEE,
        RECTIFIEE,
        MODIFIEE
    }
}
