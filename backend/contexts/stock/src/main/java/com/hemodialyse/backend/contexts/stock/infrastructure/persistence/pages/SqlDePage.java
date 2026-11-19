package com.hemodialyse.backend.contexts.stock.infrastructure.persistence.pages;

import com.hemodialyse.backend.contexts.stock.application.pagination.DemandeDePage;

import java.util.Map;

/**
 * Fragments SQL des listes paginées du Stock : {@code ORDER BY} tiré d'une table fixe champ d'API → expression SQL (jamais le
 * texte reçu), départage stable, {@code LIMIT ? OFFSET ?}, motif {@code LIKE} échappé (« contient », insensible à la casse).
 */
public final class SqlDePage {

    private SqlDePage() {
    }

    /**
     * @param colonnes  champ d'API → expression SQL (la liste blanche de la liste)
     * @param departage expressions ajoutées après le tri demandé pour un ordre total (ex. {@code "reference DESC, id"})
     */
    public static String ordre(DemandeDePage demande, Map<String, String> colonnes, String departage) {
        String expression = colonnes.get(demande.champ());
        if (expression == null) {
            throw new IllegalStateException("Champ de tri sans colonne : " + demande.champ());
        }
        return " ORDER BY " + expression + (demande.descendant() ? " DESC" : " ASC")
                + (departage == null || departage.isBlank() ? "" : ", " + departage);
    }

    public static String limite() {
        return " LIMIT ? OFFSET ?";
    }

    /** {@code %terme%} avec {@code \ % _} échappés ; à utiliser avec {@code LOWER(col) LIKE ? ESCAPE '\'}. */
    public static String contient(String termeEnMinuscules) {
        return "%" + termeEnMinuscules.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
