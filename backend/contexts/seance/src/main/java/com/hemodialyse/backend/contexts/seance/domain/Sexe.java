package com.hemodialyse.backend.contexts.seance.domain;

import java.util.Locale;

/**
 * Sexe affiché par la répartition du tableau de bord. {@code M} et {@code H} (après suppression des espaces et mise en majuscules)
 * valent {@link #M} ; {@code F} vaut {@link #F} ; tout le reste, vide ou absent compris, est {@link #AUTRE} (reproduit).
 */
public enum Sexe {
    M, F, AUTRE;

    public static Sexe depuis(String brut) {
        if (brut == null) return AUTRE;
        String normalise = brut.trim().toUpperCase(Locale.ROOT);
        if (normalise.equals("M") || normalise.equals("H")) return M;
        if (normalise.equals("F")) return F;
        return AUTRE;
    }
}
