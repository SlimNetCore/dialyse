package com.hemodialyse.backend.contexts.patientcouverture.domain;

public enum TypeAssure {
    ASSURE_LUI_MEME,
    CONJOINT,
    ENFANT,
    AUTRE;

    public static TypeAssure fromString(String v) {
        if (v == null || v.isBlank()) return ASSURE_LUI_MEME;
        try {
            return TypeAssure.valueOf(v);
        } catch (IllegalArgumentException e) {
            return AUTRE;
        }
    }
}