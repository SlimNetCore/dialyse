package com.hemodialyse.backend.contexts.gmao.domain.intervention;

import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;

import java.time.Instant;

/** Un événement de la chronologie d'une intervention : qui a fait quoi, quand ; {@code detail} précise le motif d'une rectification. */
public record EvenementDeChronologie(Type type, Instant le, UtilisateurId par, String detail) {

    public enum Type {
        CREEE, DEMARREE, TERMINEE, ANNULEE, RECTIFIEE, MODIFIEE
    }
}
