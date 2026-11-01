package com.hemodialyse.backend.contexts.organisation.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.SocieteId;

public class Centre {
    private final CentreId id;
    private SocieteId societeId;
    private final String code;
    private String nom;
    private String fuseauHoraire;
    private int capaciteTheorique;
    private boolean actif;

    public Centre(CentreId id, SocieteId societeId, String code, String nom, String fuseauHoraire, int capaciteTheorique, boolean actif) {
        if (id == null) throw new IllegalArgumentException("Centre requis");
        if (societeId == null) throw new IllegalArgumentException("Societe requise");
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Code requis");
        if (nom == null || nom.isBlank()) throw new IllegalArgumentException("Nom requis");
        this.id = id;
        this.societeId = societeId;
        this.code = code;
        this.nom = nom;
        this.fuseauHoraire = fuseauHoraire == null ? "UTC" : fuseauHoraire;
        this.capaciteTheorique = capaciteTheorique;
        this.actif = actif;
    }

    public CentreId id() {
        return id;
    }

    public SocieteId societeId() {
        return societeId;
    }

    public String code() {
        return code;
    }

    public String nom() {
        return nom;
    }

    public String fuseauHoraire() {
        return fuseauHoraire;
    }

    public int capaciteTheorique() {
        return capaciteTheorique;
    }

    public boolean actif() {
        return actif;
    }

    public void modifierParametres(String nom, String fuseauHoraire, int capaciteTheorique, boolean actif) {
        if (nom != null && !nom.isBlank()) this.nom = nom;
        if (fuseauHoraire != null && !fuseauHoraire.isBlank()) this.fuseauHoraire = fuseauHoraire;
        this.capaciteTheorique = capaciteTheorique;
        this.actif = actif;
    }

    public void transferer(SocieteId nouvelleSociete) {
        if (nouvelleSociete == null) throw new IllegalArgumentException("Societe requise");
        this.societeId = nouvelleSociete;
    }
}
