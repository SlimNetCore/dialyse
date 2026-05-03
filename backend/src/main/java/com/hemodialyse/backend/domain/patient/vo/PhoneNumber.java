package com.hemodialyse.backend.domain.patient.vo;

/**
 * Value Object for phone numbers with light international normalization.
 */
public record PhoneNumber(String value) {

    public PhoneNumber {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Téléphone obligatoire");
        }

        String raw = value.trim().replace(" ", "").replace("-", "").replace("(", "").replace(")", "");
        if (raw.startsWith("00")) {
            raw = "+" + raw.substring(2);
        }

        String digits = raw.startsWith("+") ? raw.substring(1) : raw;
        if (!digits.matches("\\d{8,15}")) {
            throw new IllegalArgumentException("Téléphone invalide");
        }

        value = raw.startsWith("+") ? "+" + digits : digits;
    }

    public static PhoneNumber of(String value) {
        return new PhoneNumber(value);
    }
}

