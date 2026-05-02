package com.hemodialyse.backend.domain.assure.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public class AssurePatientAssignment {
    private UUID id;
    private UUID patientId;
    private String numeroAssurance;
    private UUID centerId;
    private boolean primary;
    private OffsetDateTime dateAffectation;
    private LocalDate dateDebutAffectation;
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
    public boolean isPrimary() { return primary; }
    public void setPrimary(boolean v) { this.primary = v; }
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

    /**
     * Un enregistrement est actif si sa date de fin n'est pas encore définie
     */
    public boolean isActif() {
        return dateFinAffectation == null;
    }
}
