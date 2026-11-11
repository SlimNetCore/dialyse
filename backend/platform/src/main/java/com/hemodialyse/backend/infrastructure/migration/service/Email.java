package com.hemodialyse.backend.infrastructure.migration.service;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Adresse de courriel normalisée, utilisée par l'outil de reprise ({@link LegacyValueParser}).
 */
public record Email(String value) {
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);

    public Email {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Email obligatoire");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Email invalide");
        }
        value = normalized;
    }

    public static Email of(String value) {
        return new Email(value);
    }
}
