package com.hemodialyse.backend.contexts.organisation.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

public class Machine {
    private final MachineId id;
    private final CentreId centreId;
    private String code;
    private String libelle;
    private boolean actif;

    public Machine(MachineId id, CentreId centreId, String code, String libelle, boolean actif) {
        if (id == null) throw new IllegalArgumentException("Machine requise");
        if (centreId == null) throw new IllegalArgumentException("Centre requis");
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Code requis");
        if (libelle == null || libelle.isBlank()) throw new IllegalArgumentException("Libelle requis");
        this.id = id;
        this.centreId = centreId;
        this.code = code;
        this.libelle = libelle;
        this.actif = actif;
    }

    public MachineId id() {
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

    public boolean actif() {
        return actif;
    }

    public void modifier(String code, String libelle, Boolean actif) {
        if (code != null && !code.isBlank()) this.code = code;
        if (libelle != null && !libelle.isBlank()) this.libelle = libelle;
        if (actif != null) this.actif = actif;
    }
}
