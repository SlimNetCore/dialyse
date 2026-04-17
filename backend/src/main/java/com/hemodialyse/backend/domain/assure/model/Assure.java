package com.hemodialyse.backend.domain.assure.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public class Assure {
    private String numeroAssurance;
    private UUID centerId;
    private String nom;
    private String prenom;
    private String sexe;
    private LocalDate dateNaissance;
    private String telPersonnel;
    private String telMobile;
    private String telBureau;
    private String adresse;
    private String groupeSanguin;
    private OffsetDateTime createdAt;

    public String getNumeroAssurance() { return numeroAssurance; }
    public void setNumeroAssurance(String v) { this.numeroAssurance = v; }
    public UUID getCenterId() { return centerId; }
    public void setCenterId(UUID v) { this.centerId = v; }
    public String getNom() { return nom; }
    public void setNom(String v) { this.nom = v; }
    public String getPrenom() { return prenom; }
    public void setPrenom(String v) { this.prenom = v; }
    public String getSexe() { return sexe; }
    public void setSexe(String v) { this.sexe = v; }
    public LocalDate getDateNaissance() { return dateNaissance; }
    public void setDateNaissance(LocalDate v) { this.dateNaissance = v; }
    public String getTelPersonnel() { return telPersonnel; }
    public void setTelPersonnel(String v) { this.telPersonnel = v; }
    public String getTelMobile() { return telMobile; }
    public void setTelMobile(String v) { this.telMobile = v; }
    public String getTelBureau() { return telBureau; }
    public void setTelBureau(String v) { this.telBureau = v; }
    public String getAdresse() { return adresse; }
    public void setAdresse(String v) { this.adresse = v; }
    public String getGroupeSanguin() { return groupeSanguin; }
    public void setGroupeSanguin(String v) { this.groupeSanguin = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}

