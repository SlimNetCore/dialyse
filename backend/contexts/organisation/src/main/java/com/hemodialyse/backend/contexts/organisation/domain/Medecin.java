package com.hemodialyse.backend.contexts.organisation.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

public class Medecin {
    private final MedecinId id;
    private final CentreId centreId;
    private String nom;
    private String prenom;
    private String specialite;

    public Medecin(MedecinId id, CentreId centreId, String nom, String prenom, String specialite) {
        if (id == null) throw new IllegalArgumentException("Medecin requis");
        if (centreId == null) throw new IllegalArgumentException("Centre requis");
        if (nom == null || nom.isBlank()) throw new IllegalArgumentException("Nom requis");
        this.id = id;
        this.centreId = centreId;
        this.nom = nom;
        this.prenom = prenom;
        this.specialite = specialite;
    }

    public MedecinId id() {
        return id;
    }

    public CentreId centreId() {
        return centreId;
    }

    public String nom() {
        return nom;
    }

    public String prenom() {
        return prenom;
    }

    public String specialite() {
        return specialite;
    }

    public void modifier(String nom, String prenom, String specialite) {
        if (nom != null && !nom.isBlank()) this.nom = nom;
        if (prenom != null) this.prenom = prenom;
        if (specialite != null) this.specialite = specialite;
    }
}
