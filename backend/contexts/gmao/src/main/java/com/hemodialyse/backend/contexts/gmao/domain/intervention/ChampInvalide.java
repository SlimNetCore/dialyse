package com.hemodialyse.backend.contexts.gmao.domain.intervention;

/**
 * Une donnée requise manque ou une règle de valeur est violée (R11 à R14, R16). Étend {@link IllegalArgumentException} :
 * HTTP 400 BAD_REQUEST observé, avec les messages du legacy.
 */
public class ChampInvalide extends IllegalArgumentException {
    public ChampInvalide(String message) {
        super(message);
    }
}
