package com.hemodialyse.backend.contexts.comptabilite.application;

import java.util.List;

/**
 * Une page de résultats : mêmes quatre champs que le contrat observé ({@code items}, {@code total}, {@code page}, {@code size}).
 */
public record PageDe<T>(List<T> items, long total, int page, int size) {

    /**
     * Découpe une liste déjà complète ; une page hors limites est vide.
     */
    public static <T> PageDe<T> decoupee(List<T> tous, int page, int taille) {
        int debut = (int) Math.min((long) page * taille, tous.size());
        return new PageDe<>(tous.subList(debut, Math.min(debut + taille, tous.size())), tous.size(), page, taille);
    }
}
