package com.hemodialyse.backend.contexts.organisation.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/**
 * Médecin référencé par un centre. Un médecin n'est jamais supprimé (il est référencé par les patients et les prescriptions) :
 * il est désactivé et disparaît des listes de sélection.
 */
public class Medecin {
    private final MedecinId id;
    private final CentreId centreId;
    private String nom;
    private String prenom;
    private String specialite;
    private boolean actif;

    public Medecin(MedecinId id, CentreId centreId, String nom, String prenom, String specialite) {
        this(id, centreId, nom, prenom, specialite, true);
    }

    public Medecin(MedecinId id, CentreId centreId, String nom, String prenom, String specialite, boolean actif) {
        if (id == null) throw new IllegalArgumentException("Medecin requis");
        if (centreId == null) throw new IllegalArgumentException("Centre requis");
        if (nom == null || nom.isBlank()) throw new IllegalArgumentException("Nom requis");
        this.id = id;
        this.centreId = centreId;
        this.nom = nom;
        this.prenom = prenom;
        this.specialite = specialite;
        this.actif = actif;
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

    public boolean actif() {
        return actif;
    }

    public void modifier(String nom, String prenom, String specialite) {
        if (nom != null && !nom.isBlank()) this.nom = nom;
        if (prenom != null) this.prenom = prenom;
        if (specialite != null) this.specialite = specialite;
    }

    /** Désactive le médecin (idempotent) : il n'est plus proposé, mais ses références restent valides. */
    public void desactiver() {
        this.actif = false;
    }
}
