package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "dossier_medical_patient")
public class DossierMedicalPatientJpaEntity {
    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false, unique = true)
    private UUID patientId;

    @Column(name = "center_id", nullable = false)
    private UUID centerId;

    @Column(name = "nephropathie_initiale", length = 255)
    private String nephropathieInitiale;

    @Column(name = "date_mise_en_dialyse")
    private LocalDate dateMiseEnDialyse;

    @Column(name = "hepatite_b_statut", length = 20)
    private String hepatiteBStatut;

    @Column(name = "hepatite_c_statut", length = 20)
    private String hepatiteCStatut;

    @Column(name = "observation_globale", columnDefinition = "TEXT")
    private String observationGlobale;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
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

    public String getNephropathieInitiale() {
        return nephropathieInitiale;
    }

    public void setNephropathieInitiale(String nephropathieInitiale) {
        this.nephropathieInitiale = nephropathieInitiale;
    }

    public LocalDate getDateMiseEnDialyse() {
        return dateMiseEnDialyse;
    }

    public void setDateMiseEnDialyse(LocalDate dateMiseEnDialyse) {
        this.dateMiseEnDialyse = dateMiseEnDialyse;
    }

    public String getHepatiteBStatut() {
        return hepatiteBStatut;
    }

    public void setHepatiteBStatut(String hepatiteBStatut) {
        this.hepatiteBStatut = hepatiteBStatut;
    }

    public String getHepatiteCStatut() {
        return hepatiteCStatut;
    }

    public void setHepatiteCStatut(String hepatiteCStatut) {
        this.hepatiteCStatut = hepatiteCStatut;
    }

    public String getObservationGlobale() {
        return observationGlobale;
    }

    public void setObservationGlobale(String observationGlobale) {
        this.observationGlobale = observationGlobale;
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

