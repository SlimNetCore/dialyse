package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "serologies_patient")
public class SerologieJpaEntity {

    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "center_id", nullable = false)
    private UUID centerId;

    @Column(name = "marqueur", nullable = false, length = 15)
    private String marqueur;

    @Column(name = "resultat", nullable = false, length = 15)
    private String resultat;

    @Column(name = "titre", precision = 10, scale = 3)
    private BigDecimal titre;

    @Column(name = "unite", length = 20)
    private String unite;

    @Column(name = "date_prelevement", nullable = false)
    private LocalDate datePrelevement;

    @Column(name = "laboratoire", length = 255)
    private String laboratoire;

    @Column(name = "date_prochain_controle")
    private LocalDate dateProchainControle;

    @Column(name = "conduite_a_tenir", columnDefinition = "TEXT")
    private String conduiteATenir;

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

    public String getMarqueur() {
        return marqueur;
    }

    public void setMarqueur(String marqueur) {
        this.marqueur = marqueur;
    }

    public String getResultat() {
        return resultat;
    }

    public void setResultat(String resultat) {
        this.resultat = resultat;
    }

    public BigDecimal getTitre() {
        return titre;
    }

    public void setTitre(BigDecimal titre) {
        this.titre = titre;
    }

    public String getUnite() {
        return unite;
    }

    public void setUnite(String unite) {
        this.unite = unite;
    }

    public LocalDate getDatePrelevement() {
        return datePrelevement;
    }

    public void setDatePrelevement(LocalDate datePrelevement) {
        this.datePrelevement = datePrelevement;
    }

    public String getLaboratoire() {
        return laboratoire;
    }

    public void setLaboratoire(String laboratoire) {
        this.laboratoire = laboratoire;
    }

    public LocalDate getDateProchainControle() {
        return dateProchainControle;
    }

    public void setDateProchainControle(LocalDate dateProchainControle) {
        this.dateProchainControle = dateProchainControle;
    }

    public String getConduiteATenir() {
        return conduiteATenir;
    }

    public void setConduiteATenir(String conduiteATenir) {
        this.conduiteATenir = conduiteATenir;
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
