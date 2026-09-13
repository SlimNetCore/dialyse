package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "allergies_patient")
public class AllergieJpaEntity {

    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "center_id", nullable = false)
    private UUID centerId;

    @Column(name = "substance_code_system", length = 10)
    private String substanceCodeSystem;

    @Column(name = "substance_code", nullable = false, length = 20)
    private String substanceCode;

    @Column(name = "substance_code_display", length = 255)
    private String substanceCodeDisplay;

    @Column(name = "categorie", nullable = false, length = 20)
    private String categorie;

    @Column(name = "criticite", nullable = false, length = 10)
    private String criticite;

    @Column(name = "type_reaction", nullable = false, length = 15)
    private String typeReaction;

    @Column(name = "manifestations", columnDefinition = "TEXT")
    private String manifestations;

    @Column(name = "date_constatation", nullable = false)
    private LocalDate dateConstatation;

    @Column(name = "statut_verification", nullable = false, length = 15)
    private String statutVerification;

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

    public String getSubstanceCodeSystem() {
        return substanceCodeSystem;
    }

    public void setSubstanceCodeSystem(String substanceCodeSystem) {
        this.substanceCodeSystem = substanceCodeSystem;
    }

    public String getSubstanceCode() {
        return substanceCode;
    }

    public void setSubstanceCode(String substanceCode) {
        this.substanceCode = substanceCode;
    }

    public String getSubstanceCodeDisplay() {
        return substanceCodeDisplay;
    }

    public void setSubstanceCodeDisplay(String substanceCodeDisplay) {
        this.substanceCodeDisplay = substanceCodeDisplay;
    }

    public String getCategorie() {
        return categorie;
    }

    public void setCategorie(String categorie) {
        this.categorie = categorie;
    }

    public String getCriticite() {
        return criticite;
    }

    public void setCriticite(String criticite) {
        this.criticite = criticite;
    }

    public String getTypeReaction() {
        return typeReaction;
    }

    public void setTypeReaction(String typeReaction) {
        this.typeReaction = typeReaction;
    }

    public String getManifestations() {
        return manifestations;
    }

    public void setManifestations(String manifestations) {
        this.manifestations = manifestations;
    }

    public LocalDate getDateConstatation() {
        return dateConstatation;
    }

    public void setDateConstatation(LocalDate dateConstatation) {
        this.dateConstatation = dateConstatation;
    }

    public String getStatutVerification() {
        return statutVerification;
    }

    public void setStatutVerification(String statutVerification) {
        this.statutVerification = statutVerification;
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
