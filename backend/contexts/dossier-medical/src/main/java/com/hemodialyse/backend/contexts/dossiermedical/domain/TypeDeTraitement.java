package com.hemodialyse.backend.contexts.dossiermedical.domain;

/** Traitement de l'anémie suivi en observance : l'EPO (dose en UI) ou le fer injectable (dose en mg). */
public enum TypeDeTraitement {
    EPO("UI", "EPO"), FER_INJECTABLE("mg", "fer injectable");

    private final String unite;
    private final String libelle;

    TypeDeTraitement(String unite, String libelle) {
        this.unite = unite;
        this.libelle = libelle;
    }

    /** Unité dans laquelle l'observance compte la dose administrée de ce traitement. */
    public String unite() {
        return unite;
    }

    public String libelle() {
        return libelle;
    }
}
