package com.hemodialyse.backend.domain.referential.admin.model;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Description d'un champ de référentiel (formulaire, validation, colonnes d'import).
 *
 * @param key           identifiant technique (clé JSON)
 * @param label         libellé français — sert aussi d'en-tête de colonne dans le modèle d'import
 * @param type          nature du champ
 * @param required      obligatoire (après application de {@code defaultValue})
 * @param maxLength     longueur maximale (texte), 0 = sans objet
 * @param allowedValues valeurs autorisées (ENUM)
 * @param defaultValue  valeur appliquée quand le champ est vide (colonne alors facultative à l'import)
 * @param referenceSlug référentiel cible (REFERENCE), résolu par {@link ReferentialKind#fromSlug(String)}
 * @param example       valeur d'exemple écrite dans le modèle d'import
 * @param aliases       autres en-têtes de colonne acceptés à l'import
 */
public record ReferentialField(String key, String label, FieldType type, boolean required, int maxLength,
                               List<String> allowedValues, String defaultValue, String referenceSlug,
                               String example, List<String> aliases) {

    public ReferentialField {
        allowedValues = allowedValues == null ? List.of() : List.copyOf(allowedValues);
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
    }

    public static ReferentialField text(String key, String label, boolean required, int maxLength,
                                        String example, String... aliases) {
        return new ReferentialField(key, label, FieldType.TEXT, required, maxLength, List.of(), null, null,
                example, List.of(aliases));
    }

    public static ReferentialField decimal(String key, String label, boolean required, String example,
                                           String... aliases) {
        return new ReferentialField(key, label, FieldType.DECIMAL, required, 0, List.of(), null, null,
                example, List.of(aliases));
    }

    public static ReferentialField phone(String key, String label, boolean required, int maxLength,
                                         String example, String... aliases) {
        return new ReferentialField(key, label, FieldType.PHONE, required, maxLength, List.of(), null, null,
                example, List.of(aliases));
    }

    public static ReferentialField enumeration(String key, String label, List<String> allowedValues,
                                               String defaultValue, String... aliases) {
        return new ReferentialField(key, label, FieldType.ENUM, true, 30, allowedValues, defaultValue, null,
                defaultValue, List.of(aliases));
    }

    public static ReferentialField reference(String key, String label, String referenceSlug, String example,
                                             String... aliases) {
        return new ReferentialField(key, label, FieldType.REFERENCE, true, 0, List.of(), null, referenceSlug,
                example, List.of(aliases));
    }

    /**
     * « Libellé (code) » → « libellecode » : casse, accents, espaces et ponctuation ignorés.
     */
    public static String normalize(String header) {
        if (header == null) return "";
        String withoutAccents = Normalizer.normalize(header, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return withoutAccents.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /**
     * Référentiel ciblé par un champ REFERENCE.
     */
    public ReferentialKind referenceKind() {
        return referenceSlug == null ? null : ReferentialKind.fromSlug(referenceSlug);
    }

    /**
     * Une colonne doit figurer dans le fichier d'import si le champ est obligatoire sans valeur par défaut.
     */
    public boolean requiredColumn() {
        return required && defaultValue == null;
    }

    /**
     * L'en-tête (déjà normalisé) désigne-t-il ce champ ?
     */
    public boolean matchesHeader(String normalizedHeader) {
        if (normalizedHeader == null || normalizedHeader.isEmpty()) return false;
        if (normalizedHeader.equals(normalize(key)) || normalizedHeader.equals(normalize(label))) return true;
        return aliases.stream().anyMatch(alias -> normalizedHeader.equals(normalize(alias)));
    }
}

