package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "assure_patient")
@IdClass(AssurePatientId.class)
public class AssurePatientJpaEntity {
    @Id
    @Column(name = "patient_id")
    private UUID patientId;

    @Id
    @Column(name = "numero_assurance")
    private String numeroAssurance;

    @Column(name = "center_id", nullable = false)
    private UUID centerId;

    @Column(name = "is_primary", nullable = false)
    private Boolean isPrimary;

    @Column(name = "date_affectation", nullable = false)
    private OffsetDateTime dateAffectation;

    public UUID getPatientId() { return patientId; }
    public void setPatientId(UUID v) { this.patientId = v; }
    public String getNumeroAssurance() { return numeroAssurance; }
    public void setNumeroAssurance(String v) { this.numeroAssurance = v; }
    public UUID getCenterId() { return centerId; }
    public void setCenterId(UUID v) { this.centerId = v; }
    public Boolean getIsPrimary() { return isPrimary; }
    public void setIsPrimary(Boolean v) { this.isPrimary = v; }
    public OffsetDateTime getDateAffectation() { return dateAffectation; }
    public void setDateAffectation(OffsetDateTime v) { this.dateAffectation = v; }
}

