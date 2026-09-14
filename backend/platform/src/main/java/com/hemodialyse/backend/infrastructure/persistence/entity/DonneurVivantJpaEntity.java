package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "donneurs_vivants")
public class DonneurVivantJpaEntity {

    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "center_id", nullable = false)
    private UUID centerId;

    @Column(name = "nom", nullable = false, length = 100)
    private String nom;

    @Column(name = "prenom", length = 100)
    private String prenom;

    @Column(name = "date_naissance")
    private LocalDate dateNaissance;

    @Column(name = "lien_parente", nullable = false, length = 20)
    private String lienParente;

    @Column(name = "telephone", length = 30)
    private String telephone;

    @Column(name = "groupe_sanguin", length = 5)
    private String groupeSanguin;

    @Column(name = "typage_hla", length = 500)
    private String typageHla;

    @Column(name = "statut_bilan", nullable = false, length = 20)
    private String statutBilan;

    @Column(name = "crossmatch_resultat", nullable = false, length = 15)
    private String crossmatchResultat;

    @Column(name = "date_crossmatch")
    private LocalDate dateCrossmatch;

    @Column(name = "bilan_realise", columnDefinition = "TEXT")
    private String bilanRealise;

    @Column(name = "contre_indications", columnDefinition = "TEXT")
    private String contreIndications;

    @Column(name = "decision_finale", columnDefinition = "TEXT")
    private String decisionFinale;

    @Column(name = "date_decision")
    private LocalDate dateDecision;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public void setPatientId(UUID patientId) {
        this.patientId = patientId;
    }

    public UUID getCenterId() {
        return centerId;
    }

    public void setCenterId(UUID centerId) {
        this.centerId = centerId;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getPrenom() {
        return prenom;
    }

    public void setPrenom(String prenom) {
        this.prenom = prenom;
    }

    public LocalDate getDateNaissance() {
        return dateNaissance;
    }

    public void setDateNaissance(LocalDate dateNaissance) {
        this.dateNaissance = dateNaissance;
    }

    public String getLienParente() {
        return lienParente;
    }

    public void setLienParente(String lienParente) {
        this.lienParente = lienParente;
    }

    public String getTelephone() {
        return telephone;
    }

    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }

    public String getGroupeSanguin() {
        return groupeSanguin;
    }

    public void setGroupeSanguin(String groupeSanguin) {
        this.groupeSanguin = groupeSanguin;
    }

    public String getTypageHla() {
        return typageHla;
    }

    public void setTypageHla(String typageHla) {
        this.typageHla = typageHla;
    }

    public String getStatutBilan() {
        return statutBilan;
    }

    public void setStatutBilan(String statutBilan) {
        this.statutBilan = statutBilan;
    }

    public String getCrossmatchResultat() {
        return crossmatchResultat;
    }

    public void setCrossmatchResultat(String crossmatchResultat) {
        this.crossmatchResultat = crossmatchResultat;
    }

    public LocalDate getDateCrossmatch() {
        return dateCrossmatch;
    }

    public void setDateCrossmatch(LocalDate dateCrossmatch) {
        this.dateCrossmatch = dateCrossmatch;
    }

    public String getBilanRealise() {
        return bilanRealise;
    }

    public void setBilanRealise(String bilanRealise) {
        this.bilanRealise = bilanRealise;
    }

    public String getContreIndications() {
        return contreIndications;
    }

    public void setContreIndications(String contreIndications) {
        this.contreIndications = contreIndications;
    }

    public String getDecisionFinale() {
        return decisionFinale;
    }

    public void setDecisionFinale(String decisionFinale) {
        this.decisionFinale = decisionFinale;
    }

    public LocalDate getDateDecision() {
        return dateDecision;
    }

    public void setDateDecision(LocalDate dateDecision) {
        this.dateDecision = dateDecision;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
