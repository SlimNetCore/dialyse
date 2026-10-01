package com.hemodialyse.backend.domain.gmao.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entrée de la ligne de temps d'une intervention : qui a fait quoi, et quand (instant UTC).
 */
public record EvenementIntervention(Type type, OffsetDateTime at, UUID par) {

    public enum Type {
        CREEE,
        DEMARREE,
        TERMINEE,
        ANNULEE,
        MODIFIEE
    }
}
