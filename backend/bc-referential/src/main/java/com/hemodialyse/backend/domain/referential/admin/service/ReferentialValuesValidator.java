package com.hemodialyse.backend.domain.referential.admin.service;

import com.hemodialyse.backend.domain.referential.admin.model.ReferentialField;
import com.hemodialyse.backend.domain.referential.admin.model.ReferentialKind;
import com.hemodialyse.backend.domain.referential.admin.model.ValidationIssue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Contrôle et normalise les valeurs d'une ligne de référentiel selon la description de ses champs.
 * Utilisé à l'identique pour la saisie au formulaire et pour chaque ligne d'un fichier importé.
 */
public final class ReferentialValuesValidator {

    /**
     * Borne de {@code numeric(10,2)}.
     */
    private static final int MAX_INTEGER = 999;
    private static final BigDecimal MAX_DECIMAL = new BigDecimal("99999999.99");
    private static final Pattern PHONE = Pattern.compile("^[+0-9 ().\\-/]{6,}$");

    private static String clean(String value) {
        if (value == null) return null;
        String trimmed = value.replace('\u00A0', ' ').trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * « en panne » → EN_PANNE ; « Hors-service » → HORS_SERVICE.
     */
    private static String toEnumCode(String value) {
        String noAccents = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s\\-]+", "_");
    }

    private static ValidationIssue issue(ReferentialField field, String code, String message, Map<String, String> params) {
        return new ValidationIssue(0, field.key(), code, message, params);
    }

    public Result validate(ReferentialKind kind, Map<String, String> raw, ReferenceResolver resolver) {
        Map<String, String> values = new LinkedHashMap<>();
        List<ValidationIssue> issues = new ArrayList<>();
        Map<String, String> input = raw == null ? Map.of() : raw;

        for (ReferentialField field : kind.fields()) {
            String value = clean(input.get(field.key()));
            if (value == null) value = field.defaultValue();
            if (value == null) {
                if (field.required()) {
                    issues.add(issue(field, "REQUIRED", "« " + field.label() + " » est obligatoire.", Map.of()));
                }
                values.put(field.key(), null);
                continue;
            }
            normalize(field, value, resolver, issues).ifPresent(v -> values.put(field.key(), v));
        }
        return new Result(values, issues);
    }

    private Optional<String> normalize(ReferentialField field, String value, ReferenceResolver resolver,
                                       List<ValidationIssue> issues) {
        return switch (field.type()) {
            case TEXT -> checkLength(field, value, issues);
            case PHONE -> {
                if (!PHONE.matcher(value).matches()) {
                    issues.add(issue(field, "INVALID_PHONE",
                            "« " + field.label() + " » : « " + value + " » n'est pas un numéro de téléphone valide.",
                            Map.of("value", value)));
                    yield Optional.empty();
                }
                yield checkLength(field, value, issues);
            }
            case DECIMAL -> parseDecimal(field, value, issues);
            case INTEGER -> parseInteger(field, value, issues);
            case ENUM -> {
                String code = toEnumCode(value);
                if (!field.allowedValues().contains(code)) {
                    String allowed = String.join(", ", field.allowedValues());
                    issues.add(issue(field, "INVALID_VALUE",
                            "« " + field.label() + " » : « " + value + " » n'est pas une valeur autorisée (" + allowed + ").",
                            Map.of("value", value, "allowed", allowed)));
                    yield Optional.empty();
                }
                yield Optional.of(code);
            }
            case REFERENCE -> {
                ReferentialKind target = field.referenceKind();
                Optional<UUID> id = resolver.resolve(target, value);
                if (id.isEmpty()) {
                    issues.add(issue(field, "REFERENCE_NOT_FOUND",
                            "« " + field.label() + " » : « " + value + " » est introuvable dans « " + target.label()
                                    + " » — créez-le d'abord (ou importez d'abord le fichier « " + target.label() + " »).",
                            Map.of("value", value, "target", target.slug())));
                    yield Optional.empty();
                }
                yield Optional.of(id.get().toString());
            }
        };
    }

    private Optional<String> checkLength(ReferentialField field, String value, List<ValidationIssue> issues) {
        if (field.maxLength() > 0 && value.length() > field.maxLength()) {
            issues.add(issue(field, "TOO_LONG",
                    "« " + field.label() + " » dépasse " + field.maxLength() + " caractères (" + value.length() + ").",
                    Map.of("max", String.valueOf(field.maxLength()), "length", String.valueOf(value.length()))));
            return Optional.empty();
        }
        return Optional.of(value);
    }

    private Optional<String> parseInteger(ReferentialField field, String value, List<ValidationIssue> issues) {
        String compact = value.replaceAll("[\\s\\u00A0\\u202F]", "");
        int number;
        try {
            number = Integer.parseInt(compact);
        } catch (NumberFormatException e) {
            issues.add(issue(field, "INVALID_INTEGER",
                    "« " + field.label() + " » : « " + value + " » n'est pas un nombre entier (ex. 12).",
                    Map.of("value", value)));
            return Optional.empty();
        }
        if (number < 1 || number > MAX_INTEGER) {
            issues.add(issue(field, "INTEGER_OUT_OF_RANGE",
                    "« " + field.label() + " » doit être compris entre 1 et " + MAX_INTEGER + ".",
                    Map.of("value", value, "max", String.valueOf(MAX_INTEGER))));
            return Optional.empty();
        }
        return Optional.of(String.valueOf(number));
    }

    private Optional<String> parseDecimal(ReferentialField field, String value, List<ValidationIssue> issues) {
        String compact = value.replaceAll("[\\s\\u00A0\\u202F]", "").replace(',', '.');
        BigDecimal number;
        try {
            number = new BigDecimal(compact);
        } catch (NumberFormatException e) {
            issues.add(issue(field, "INVALID_NUMBER",
                    "« " + field.label() + " » : « " + value + " » n'est pas un nombre (ex. 5600 ou 5600,50).",
                    Map.of("value", value)));
            return Optional.empty();
        }
        if (number.signum() < 0) {
            issues.add(issue(field, "NEGATIVE_NUMBER", "« " + field.label() + " » ne peut pas être négatif.",
                    Map.of("value", value)));
            return Optional.empty();
        }
        BigDecimal scaled = number.setScale(2, RoundingMode.HALF_UP);
        if (scaled.compareTo(MAX_DECIMAL) > 0) {
            issues.add(issue(field, "NUMBER_TOO_LARGE",
                    "« " + field.label() + " » est trop grand (maximum " + MAX_DECIMAL.toPlainString() + ").",
                    Map.of("value", value, "max", MAX_DECIMAL.toPlainString())));
            return Optional.empty();
        }
        return Optional.of(scaled.toPlainString());
    }

    /**
     * Résout la valeur d'un champ REFERENCE (identifiant ou code) en identifiant existant dans le centre.
     */
    @FunctionalInterface
    public interface ReferenceResolver {
        Optional<UUID> resolve(ReferentialKind target, String idOrCode);
    }

    /**
     * Valeurs normalisées + anomalies (vide = valeurs acceptées).
     */
    public record Result(Map<String, String> values, List<ValidationIssue> issues) {
        public boolean valid() {
            return issues.isEmpty();
        }
    }
}

