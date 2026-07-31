package com.hemodialyse.backend.domain.patient.vo;

import java.util.UUID;

/** Value Object — strongly typed patient identifier */
public record PatientId(UUID value) {
    public PatientId {
        if (value == null) throw new IllegalArgumentException("PatientId cannot be null");
    }
    public static PatientId of(UUID v) { return new PatientId(v); }
    public static PatientId generate() { return new PatientId(UUID.randomUUID()); }
    @Override public String toString() { return value.toString(); }
}

