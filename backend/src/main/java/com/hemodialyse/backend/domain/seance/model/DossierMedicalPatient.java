package com.hemodialyse.backend.domain.seance.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public class DossierMedicalPatient {
    private UUID id;
    private UUID patientId;
    private UUID centerId;
    private String nephropathieInitiale;
    private LocalDate dateMiseEnDialyse;
    private String hepatiteBStatut;
    private String hepatiteCStatut;
    private String observationGlobale;
    private OffsetDateTime createdAt;
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

    public void setNephropathieInitiale(String v) {
        this.nephropathieInitiale = v;
    }

    public LocalDate getDateMiseEnDialyse() {
        return dateMiseEnDialyse;
    }

    public void setDateMiseEnDialyse(LocalDate v) {
        this.dateMiseEnDialyse = v;
    }

    public String getHepatiteBStatut() {
        return hepatiteBStatut;
    }

    public void setHepatiteBStatut(String v) {
        this.hepatiteBStatut = v;
    }

    public String getHepatiteCStatut() {
        return hepatiteCStatut;
    }

    public void setHepatiteCStatut(String v) {
        this.hepatiteCStatut = v;
    }

    public String getObservationGlobale() {
        return observationGlobale;
    }

    public void setObservationGlobale(String v) {
        this.observationGlobale = v;
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

