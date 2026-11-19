package com.hemodialyse.backend.contexts.stock.application.pagination;

import java.util.Set;

/**
 * Paramètres bruts {@code page}, {@code size} et {@code sort} d'une liste paginée du Stock, tels que reçus (absents :
 * {@code null}) ; {@link #lire} les valide (contrat {@code pagination-serveur}).
 */
public record ParametresDePage(Integer page, Integer size, String sort) {

    /** @throws PaginationInvalide {@code PAGE_INVALIDE}, {@code TAILLE_INVALIDE} ou {@code TRI_INVALIDE} */
    public DemandeDePage lire(Set<String> champsTriables, String champParDefaut, boolean descendantParDefaut) {
        return DemandeDePage.lire(page, size, sort, champsTriables, champParDefaut, descendantParDefaut);
    }
}
