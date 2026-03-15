package com.hemodialyse.backend.domain.patient.vo;

/** Value Object — Insured person information (embedded in Patient aggregate) */
public record AssureInfo(
    String sexe,
    String nom,
    String prenom,
    String dateNaissance,
    String telPersonnel,
    String adresse,
    String groupeSanguin,
    String telMobile,
    String telBureau
) {
    public static AssureInfo empty() {
        return new AssureInfo(null, null, null, null, null, null, null, null, null);
    }
}

