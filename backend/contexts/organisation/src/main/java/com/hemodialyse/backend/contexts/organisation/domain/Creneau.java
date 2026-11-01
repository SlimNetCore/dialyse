package com.hemodialyse.backend.contexts.organisation.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

public class Creneau {
    private final CreneauId id;
    private final CentreId centreId;
    private String code;
    private String libelle;

    public Creneau(CreneauId id, CentreId centreId, String code, String libelle) {
        if (id == null) throw new IllegalArgumentException("Creneau requis");
        if (centreId == null) throw new IllegalArgumentException("Centre requis");
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Code requis");
        if (libelle == null || libelle.isBlank()) throw new IllegalArgumentException("Libelle requis");
        this.id = id;
        this.centreId = centreId;
        this.code = code;
        this.libelle = libelle;
    }

    public CreneauId id() {
        return id;
    }

    public CentreId centreId() {
        return centreId;
    }

    public String code() {
        return code;
    }

    public String libelle() {
        return libelle;
    }

    public void modifier(String code, String libelle) {
        if (code != null && !code.isBlank()) this.code = code;
        if (libelle != null && !libelle.isBlank()) this.libelle = libelle;
    }
}
