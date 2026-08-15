package com.hemodialyse.backend.domain.comptabilite.valueobject;

/**
 * Code journal SCF. VE = Ventes, BQ = Banque, CA = Caisse.
 * Le module v1 ne gère que VE (facturation) et BQ/CA (règlements).
 */
public enum JournalCode {
    VE("Ventes"),
    BQ("Banque"),
    CA("Caisse");

    private final String libelle;

    JournalCode(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}

