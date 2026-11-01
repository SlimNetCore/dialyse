package com.hemodialyse.backend.contexts.organisation.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.SocieteId;

public class Societe {
    private final SocieteId id;
    private String code;
    private String raisonSociale;
    private boolean actif;

    public Societe(SocieteId id, String code, String raisonSociale, boolean actif) {
        if (id == null) throw new IllegalArgumentException("Societe requise");
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Code requis");
        if (raisonSociale == null || raisonSociale.isBlank())
            throw new IllegalArgumentException("Raison sociale requise");
        this.id = id;
        this.code = code;
        this.raisonSociale = raisonSociale;
        this.actif = actif;
    }

    public SocieteId id() {
        return id;
    }

    public String code() {
        return code;
    }

    public String raisonSociale() {
        return raisonSociale;
    }

    public boolean actif() {
        return actif;
    }

    public void modifier(String code, String raisonSociale, boolean actif) {
        if (code != null && !code.isBlank()) this.code = code;
        if (raisonSociale != null && !raisonSociale.isBlank()) this.raisonSociale = raisonSociale;
        this.actif = actif;
    }
}
