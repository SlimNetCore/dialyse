package com.hemodialyse.backend.contexts.organisation.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

public class Salle {
    private final SalleId id;
    private final CentreId centreId;
    private String code;
    private String nom;
    private boolean ouverte;

    public Salle(SalleId id, CentreId centreId, String code, String nom, boolean ouverte) {
        if (id == null) throw new IllegalArgumentException("Salle requise");
        if (centreId == null) throw new IllegalArgumentException("Centre requis");
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Code requis");
        if (nom == null || nom.isBlank()) throw new IllegalArgumentException("Nom requis");
        this.id = id;
        this.centreId = centreId;
        this.code = code;
        this.nom = nom;
        this.ouverte = ouverte;
    }

    public SalleId id() {
        return id;
    }

    public CentreId centreId() {
        return centreId;
    }

    public String code() {
        return code;
    }

    public String nom() {
        return nom;
    }

    public boolean ouverte() {
        return ouverte;
    }

    public void ouvrir() {
        this.ouverte = true;
    }

    public void fermer() {
        this.ouverte = false;
    }

    public void modifier(String code, String nom) {
        if (code != null && !code.isBlank()) this.code = code;
        if (nom != null && !nom.isBlank()) this.nom = nom;
    }
}
