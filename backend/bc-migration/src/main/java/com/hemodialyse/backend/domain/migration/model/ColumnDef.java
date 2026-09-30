package com.hemodialyse.backend.domain.migration.model;

import com.hemodialyse.backend.domain.referential.admin.model.ReferentialField;

import java.util.List;
import java.util.Map;

/**
 * Colonne attendue dans un fichier de reprise.
 *
 * @param key           clé technique
 * @param label         libellé français, en-tête du modèle
 * @param type          nature de la valeur
 * @param required      valeur obligatoire (après application de {@code defaultValue})
 * @param maxLength     longueur maximale (texte), 0 = sans objet
 * @param allowedValues valeurs autorisées (ENUM)
 * @param defaultValue  valeur appliquée si vide (la colonne devient alors facultative)
 * @param synonyms      valeurs de l'ancien système reconnues d'office (ex. « Décédé » → DECEDE)
 * @param example       valeur d'exemple du modèle
 * @param aliases       autres en-têtes acceptés
 */
public record ColumnDef(String key, String label, ColumnType type, boolean required, int maxLength,
                        List<String> allowedValues, String defaultValue, Map<String, String> synonyms,
                        String example, List<String> aliases) {

    public ColumnDef {
        allowedValues = allowedValues == null ? List.of() : List.copyOf(allowedValues);
        synonyms = synonyms == null ? Map.of() : Map.copyOf(synonyms);
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
    }

    public static ColumnDef text(String key, String label, boolean required, int maxLength, String example, String... aliases) {
        return new ColumnDef(key, label, ColumnType.TEXT, required, maxLength, null, null, null, example, List.of(aliases));
    }

    public static ColumnDef of(String key, String label, ColumnType type, boolean required, String example, String... aliases) {
        return new ColumnDef(key, label, type, required, type == ColumnType.PHONE ? 50 : 0, null, null, null, example,
                List.of(aliases));
    }

    public static ColumnDef enumeration(String key, String label, List<String> allowed, String defaultValue,
                                        Map<String, String> synonyms, String example, String... aliases) {
        return new ColumnDef(key, label, ColumnType.ENUM, true, 0, allowed, defaultValue, synonyms, example, List.of(aliases));
    }

    /**
     * La colonne doit figurer dans le fichier : obligatoire et sans valeur par défaut.
     */
    public boolean requiredColumn() {
        return required && defaultValue == null;
    }

    public boolean matchesHeader(String normalizedHeader) {
        if (normalizedHeader == null || normalizedHeader.isEmpty()) return false;
        if (normalizedHeader.equals(ReferentialField.normalize(key)) || normalizedHeader.equals(ReferentialField.normalize(label))) {
            return true;
        }
        return aliases.stream().anyMatch(a -> normalizedHeader.equals(ReferentialField.normalize(a)));
    }
}

