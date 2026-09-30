package com.hemodialyse.backend.domain.referential.admin.model;

import java.util.List;

/**
 * Contenu tabulaire d'un fichier d'import (CSV / Excel), indépendant du format d'origine.
 *
 * @param headers en-têtes de colonne (première ligne non vide)
 * @param rows    lignes de données, avec leur numéro de ligne dans le fichier pour les messages d'erreur
 */
public record ImportTable(List<String> headers, List<Row> rows) {

    public ImportTable {
        headers = headers == null ? List.of() : List.copyOf(headers);
        rows = rows == null ? List.of() : List.copyOf(rows);
    }

    /**
     * @param lineNumber numéro de ligne tel que vu par l'utilisateur dans le fichier (1 = en-têtes)
     * @param cells      valeurs brutes, dans l'ordre des en-têtes
     */
    public record Row(int lineNumber, List<String> cells) {
        public Row {
            cells = cells == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(cells));
        }

        public String cell(int index) {
            return index < cells.size() ? cells.get(index) : null;
        }

        public boolean isBlank() {
            return cells.stream().allMatch(c -> c == null || c.isBlank());
        }
    }
}

