package com.hemodialyse.backend.domain.migration.service;

import com.hemodialyse.backend.domain.migration.model.ColumnDef;
import com.hemodialyse.backend.domain.referential.admin.model.ValidationIssue;
import com.hemodialyse.backend.domain.shared.vo.Email;
import com.hemodialyse.backend.domain.shared.vo.PhoneNumber;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Normalise les valeurs d'une ligne de fichier de reprise selon ses colonnes.
 * Sortie : dates en AAAA-MM-JJ, booléens « true »/« false », énumérations en code cible, jours « LUN,MER,VEN ».
 */
public final class LegacyValueParser {

    /**
     * Ordre de {@code JoursDialyse} : dimanche → samedi.
     */
    public static final List<String> DAY_CODES = List.of("DIM", "LUN", "MAR", "MER", "JEU", "VEN", "SAM");
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d-M-uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d.M.uuuu").withResolverStyle(ResolverStyle.STRICT));
    private static final Set<String> TRUE = Set.of("OUI", "O", "YES", "Y", "1", "X", "VRAI", "TRUE");
    private static final Set<String> FALSE = Set.of("NON", "N", "NO", "0", "FAUX", "FALSE");

    private static String checkLength(ColumnDef column, String value, List<ValidationIssue> issues) {
        if (column.maxLength() > 0 && value.length() > column.maxLength()) {
            issues.add(issue(column, "TOO_LONG", "« " + column.label() + " » dépasse " + column.maxLength() + " caractères.",
                    Map.of("max", String.valueOf(column.maxLength()))));
            return null;
        }
        return value;
    }

    static LocalDate parseDate(String value) {
        String candidate = value.trim();
        // Date Excel avec heure (« 2019-01-02 00:00:00 » / « 02/01/2019 00:00 »).
        int space = candidate.indexOf(' ');
        if (space > 0) candidate = candidate.substring(0, space);
        if (candidate.length() > 10 && candidate.charAt(10) == 'T') candidate = candidate.substring(0, 10);
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(candidate, format);
            } catch (DateTimeParseException ignored) {
                // format suivant
            }
        }
        return null;
    }

    /**
     * « Décédé » → DECEDE ; « Lui-même » → LUI_MEME.
     */
    public static String toCode(String value) {
        if (value == null) return "";
        String noAccents = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9+]+", "_").replaceAll("^_+|_+$", "");
    }

    private static String clean(String value) {
        if (value == null) return null;
        String trimmed = value.replace('\u00A0', ' ').trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static ValidationIssue issue(ColumnDef column, String code, String message, Map<String, String> params) {
        return new ValidationIssue(0, column.key(), code, message, params);
    }

    /**
     * @param valueMappings correspondances du centre : colonne → (valeur normalisée → valeur cible)
     */
    public Result parse(List<ColumnDef> columns, Map<String, String> raw, Map<String, Map<String, String>> valueMappings) {
        Map<String, String> values = new LinkedHashMap<>();
        List<ValidationIssue> issues = new ArrayList<>();
        for (ColumnDef column : columns) {
            String value = clean(raw.get(column.key()));
            if (value == null) value = column.defaultValue();
            if (value == null) {
                if (column.required()) {
                    issues.add(issue(column, "REQUIRED", "« " + column.label() + " » est obligatoire.", Map.of()));
                }
                values.put(column.key(), null);
                continue;
            }
            String parsed = parseValue(column, value, valueMappings.getOrDefault(column.key(), Map.of()), issues);
            if (parsed != null) values.put(column.key(), parsed);
        }
        return new Result(values, issues);
    }

    private String parseValue(ColumnDef column, String value, Map<String, String> mapped, List<ValidationIssue> issues) {
        return switch (column.type()) {
            case TEXT, REFERENCE -> checkLength(column, value, issues);
            case DATE -> {
                LocalDate date = parseDate(value);
                if (date == null) {
                    issues.add(issue(column, "INVALID_DATE", "« " + column.label() + " » : « " + value
                            + " » n'est pas une date (JJ/MM/AAAA).", Map.of("value", value)));
                    yield null;
                }
                yield date.toString();
            }
            case INTEGER -> {
                String digits = value.replaceAll("\\s", "");
                if (!digits.matches("\\d{1,4}")) {
                    issues.add(issue(column, "INVALID_NUMBER", "« " + column.label() + " » : « " + value
                            + " » n'est pas un nombre entier.", Map.of("value", value)));
                    yield null;
                }
                yield String.valueOf(Integer.parseInt(digits));
            }
            case BOOLEAN -> {
                String code = toCode(value);
                if (TRUE.contains(code)) yield "true";
                if (FALSE.contains(code)) yield "false";
                issues.add(issue(column, "INVALID_BOOLEAN", "« " + column.label() + " » : « " + value
                        + " » doit valoir oui ou non.", Map.of("value", value)));
                yield null;
            }
            case PHONE -> {
                try {
                    yield PhoneNumber.of(value.replace(".", "").replace("/", "")).value();
                } catch (IllegalArgumentException invalid) {
                    issues.add(issue(column, "INVALID_PHONE", "« " + column.label() + " » : « " + value
                            + " » n'est pas un numéro de téléphone valide.", Map.of("value", value)));
                    yield null;
                }
            }
            case EMAIL -> {
                try {
                    yield checkLength(column, Email.of(value).value(), issues);
                } catch (IllegalArgumentException invalid) {
                    issues.add(issue(column, "INVALID_EMAIL", "« " + column.label() + " » : « " + value
                            + " » n'est pas une adresse e-mail valide.", Map.of("value", value)));
                    yield null;
                }
            }
            case ENUM -> {
                String code = toCode(value);
                String target = mapped.getOrDefault(code, column.synonyms().getOrDefault(code, code));
                if (!column.allowedValues().contains(target)) {
                    String allowed = String.join(", ", column.allowedValues());
                    issues.add(issue(column, "UNKNOWN_VALUE", "« " + column.label() + " » : la valeur « " + value
                                    + " » n'est pas reconnue — associez-la à une valeur (" + allowed + ").",
                            Map.of("value", value, "source", code, "allowed", allowed)));
                    yield null;
                }
                yield target;
            }
            case DAYS -> parseDays(column, value, issues);
        };
    }

    private String parseDays(ColumnDef column, String value, List<ValidationIssue> issues) {
        Set<String> days = new LinkedHashSet<>();
        for (String token : toCode(value).split("_+")) {
            if (token.isEmpty() || token.equals("ET")) continue;
            String day = token.length() >= 3 ? token.substring(0, 3) : "";
            if (!DAY_CODES.contains(day)) {
                issues.add(issue(column, "INVALID_DAYS", "« " + column.label() + " » : « " + value
                        + " » — jours attendus : Lun, Mar, Mer, Jeu, Ven, Sam, Dim.", Map.of("value", value)));
                return null;
            }
            days.add(day);
        }
        return String.join(",", DAY_CODES.stream().filter(days::contains).toList());
    }

    public record Result(Map<String, String> values, List<ValidationIssue> issues) {
        public boolean valid() {
            return issues.isEmpty();
        }
    }
}





