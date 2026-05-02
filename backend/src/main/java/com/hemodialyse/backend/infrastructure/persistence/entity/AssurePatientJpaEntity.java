package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "assure_patient")
public class AssurePatientJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "numero_assurance", nullable = false)
    private String numeroAssurance;

    @Column(name = "center_id", nullable = false)
    private UUID centerId;

    @Column(name = "is_primary", nullable = false)
    private Boolean isPrimary;

    @Column(name = "date_affectation", nullable = false)
    private OffsetDateTime dateAffectation;

    @Column(name = "date_debut_affectation")
    private LocalDate dateDebutAffectation;

    @Column(name = "date_fin_affectation")
    private LocalDate dateFinAffectation;

    public UUID getId() {
        return id;
    }

    public void setId(UUID v) {
        this.id = v;
    }
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

    public LocalDate getDateDebutAffectation() {
        return dateDebutAffectation;
    }

    public void setDateDebutAffectation(LocalDate v) {
        this.dateDebutAffectation = v;
    }

    public LocalDate getDateFinAffectation() {
        return dateFinAffectation;
    }

    public void setDateFinAffectation(LocalDate v) {
        this.dateFinAffectation = v;
    }
}
