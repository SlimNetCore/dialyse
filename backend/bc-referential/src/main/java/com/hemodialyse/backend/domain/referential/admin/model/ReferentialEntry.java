package com.hemodialyse.backend.domain.referential.admin.model;

import java.util.Map;
import java.util.UUID;

/**
 * Ligne d'un référentiel.
 *
 * @param id         identifiant
 * @param values     valeurs par clé de champ (référence = identifiant de la cible)
 * @param references libellé affichable de chaque champ REFERENCE (ex. « S1 · Salle 1 »)
 */
public record ReferentialEntry(UUID id, Map<String, String> values, Map<String, String> references) {

    public ReferentialEntry {
        values = values == null ? Map.of() : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(values));
        references = references == null ? Map.of() : Map.copyOf(references);
    }
}

