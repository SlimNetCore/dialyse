package com.hemodialyse.backend.domain.patient.vo;

/** Value Object — Social security number. Business unique identifier for a patient. */
public record NumeroAssurance(String value) {
    public NumeroAssurance {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Numero assurance obligatoire");
    }
}

