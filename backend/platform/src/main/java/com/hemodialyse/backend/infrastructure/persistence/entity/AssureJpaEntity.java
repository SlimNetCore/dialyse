package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "assure")
public class AssureJpaEntity {
    @Id
    @Column(name = "numero_assurance")
    private String numeroAssurance;

    @Column(name = "center_id", nullable = false)
    private UUID centerId;

    @Column(name = "nom")
    private String nom;

    @Column(name = "prenom")
    private String prenom;

    @Column(name = "sexe")
    private String sexe;

    @Column(name = "date_naissance")
    private LocalDate dateNaissance;

    @Column(name = "tel_personnel")
    private String telPersonnel;

    @Column(name = "tel_mobile")
    private String telMobile;

    @Column(name = "tel_bureau")
    private String telBureau;

    @Column(name = "adresse")
    private String adresse;

    @Column(name = "groupe_sanguin")
    private String groupeSanguin;

    @Column(name = "created_at")
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

