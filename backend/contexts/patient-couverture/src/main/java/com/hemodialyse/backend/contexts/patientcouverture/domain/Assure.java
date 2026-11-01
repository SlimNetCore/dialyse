package com.hemodialyse.backend.contexts.patientcouverture.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;

public class Assure {
    private final AssureId id;
    private final CentreId centreId;
    private String nom;
    private String prenom;
    private String sexe;
    private LocalDate dateNaissance;
    private String telPersonnel;
    private String telMobile;
    private String telBureau;
    private String adresse;
    private String groupeSanguin;
    private TypeAssure typeAssure;

    public Assure(AssureId id, CentreId centreId, String nom, String prenom, String sexe, LocalDate dateNaissance,
                  String telPersonnel, String telMobile, String telBureau, String adresse, String groupeSanguin,
                  TypeAssure typeAssure) {
        if (id == null) throw new IllegalArgumentException("Assure requis");
        if (centreId == null) throw new IllegalArgumentException("Centre requis");
        if (nom == null || nom.isBlank()) throw new IllegalArgumentException("Nom requis");
        if (prenom == null || prenom.isBlank()) throw new IllegalArgumentException("Prenom requis");
        if (sexe == null || sexe.isBlank()) throw new IllegalArgumentException("Sexe requis");
        this.id = id;
        this.centreId = centreId;
        this.nom = nom;
        this.prenom = prenom;
        this.sexe = sexe;
        this.dateNaissance = dateNaissance;
        this.telPersonnel = telPersonnel;
        this.telMobile = telMobile;
        this.telBureau = telBureau;
        this.adresse = adresse;
        this.groupeSanguin = groupeSanguin;
        this.typeAssure = typeAssure == null ? TypeAssure.ASSURE_LUI_MEME : typeAssure;
    }

    public static Assure creer(AssureId id, CentreId centreId, String nom, String prenom, String sexe, LocalDate dateNaissance,
                               String telPersonnel, String telMobile, String telBureau, String adresse, String groupeSanguin,
                               TypeAssure typeAssure) {
        return new Assure(id, centreId, nom, prenom, sexe, dateNaissance, telPersonnel, telMobile, telBureau, adresse,
                groupeSanguin, typeAssure);
    }

    public void modifier(String nom, String prenom, String sexe, LocalDate dateNaissance,
                         String telPersonnel, String telMobile, String telBureau, String adresse, String groupeSanguin,
                         TypeAssure typeAssure) {
        if (nom != null && !nom.isBlank()) this.nom = nom;
        if (prenom != null && !prenom.isBlank()) this.prenom = prenom;
        if (sexe != null && !sexe.isBlank()) this.sexe = sexe;
        if (dateNaissance != null) this.dateNaissance = dateNaissance;
        if (telPersonnel != null) this.telPersonnel = telPersonnel;
        if (telMobile != null) this.telMobile = telMobile;
        if (telBureau != null) this.telBureau = telBureau;
        if (adresse != null) this.adresse = adresse;
        if (groupeSanguin != null) this.groupeSanguin = groupeSanguin;
        if (typeAssure != null) this.typeAssure = typeAssure;
    }

    public AssureId id() {
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

    public String sexe() {
        return sexe;
    }

    public LocalDate dateNaissance() {
        return dateNaissance;
    }

    public String telPersonnel() {
        return telPersonnel;
    }

    public String telMobile() {
        return telMobile;
    }

    public String telBureau() {
        return telBureau;
    }

    public String adresse() {
        return adresse;
    }

    public String groupeSanguin() {
        return groupeSanguin;
    }

    public TypeAssure typeAssure() {
        return typeAssure;
    }
}