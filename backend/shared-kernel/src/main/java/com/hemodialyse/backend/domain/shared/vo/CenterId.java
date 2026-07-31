package com.hemodialyse.backend.domain.shared.vo;

import java.util.UUID;

/** Value Object — strongly typed center identifier */
public record CenterId(UUID value) {
    public CenterId {
        if (value == null) throw new IllegalArgumentException("CenterId cannot be null");
    }
    public static CenterId of(UUID v) { return new CenterId(v); }
    public static CenterId generate() { return new CenterId(UUID.randomUUID()); }
    @Override public String toString() { return value.toString(); }
}

